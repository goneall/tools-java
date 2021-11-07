/**
 * Copyright (c) 2015 Source Auditor Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
*/
package org.spdx.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.SpdxDocument;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.tagvaluestore.TagValueStore;
import org.spdx.tools.SpdxToolsHelper.SerFileType;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

/**
 * Verifies an SPDX document and lists any verification errors
 * @author Gary O'Neall
 *
 */
public class Verify {

	static final int MIN_ARGS = 1;
	static final int MAX_ARGS = 2;
	static final int ERROR_STATUS = 1;
	private static final String JSON_SCHEMA_RESOURCE = "/resources/spdx-schema.json";

	/**
	 * @param args args[0] SPDX file path; args[1] [RDFXML|JSON|XLS|XLSX|YAML|TAG] an optional file type - if not present, file type of the to file will be used
	 */
	public static void main(String[] args) {
		if (args.length < MIN_ARGS) {
			System.err
					.println("Usage:\n Verify file\nwhere file is the file path to an SPDX file");
			System.exit(ERROR_STATUS);
		}
		if (args.length > MAX_ARGS) {
			System.out.printf("Warning: Extra arguments will be ignored");
		}
		List<String> verify = null;
		try {
			SerFileType fileType = null;
			if (args.length > 1) {
				try {
					fileType = SpdxToolsHelper.strToFileType(args[1]);
				} catch (Exception ex) {
					System.err.println("Invalid file type: "+args[1]);
					System.exit(ERROR_STATUS);
				}
			} else {
				fileType = SpdxToolsHelper.fileToFileType(new File(args[0]));
			}
			verify = verify(args[0], fileType);
			
		} catch (SpdxVerificationException e) {
			System.out.println(e.getMessage());
			System.exit(ERROR_STATUS);
		} catch (InvalidFileNameException e) {
			System.err.println("Invalid file name: "+args[0]);
			System.exit(ERROR_STATUS);
		} 
		if (verify.size() > 0) {
			System.out.println("This SPDX Document is not valid due to:");
			for (int i = 0; i < verify.size(); i++) {
				System.out.print("\t" + verify.get(i)+"\n");
			}
			System.exit(ERROR_STATUS);
		} else {
			System.out.println("This SPDX Document is valid.");
		}
	}

	/**
	 * Verify a an SPDX file
	 * @param filePath File path to the SPDX file to be verified
	 * @param fileType 
	 * @return A list of verification errors - if empty, the SPDX file is valid
	 * @throws InvalidFileNameException 
	 * @throws IOException 
	 * @throws SpdxVerificationException 
	 * @throws Errors where the SPDX file can not be parsed or the filename is invalid
	 */
	public static List<String> verify(String filePath, SerFileType fileType) throws SpdxVerificationException {
		Objects.requireNonNull(filePath);
		Objects.requireNonNull(fileType);
		File file = new File(filePath);
		if (!file.exists()) {
			throw new SpdxVerificationException("File "+filePath+" not found.");
		}
		if (!file.isFile()) {
			throw new SpdxVerificationException(filePath+" is not a file.");
		}
		ISerializableModelStore store = null;
		try {
			store = SpdxToolsHelper.fileTypeToStore(fileType);
		} catch (InvalidSPDXAnalysisException e) {
			throw new SpdxVerificationException("Error converting fileType to store",e);
		}
		SpdxDocument doc = null;
		try (InputStream is = new FileInputStream(file)) {
			String documentUri = store.deSerialize(is, false);
			doc = new SpdxDocument(store, documentUri, null, false);
		} catch (FileNotFoundException e) {
			throw new SpdxVerificationException("File "+filePath+ " not found.",e);
		} catch (IOException e) {
			throw new SpdxVerificationException("IO Error reading SPDX file",e);
		} catch (InvalidSPDXAnalysisException e) {
			throw new SpdxVerificationException("Analysis exception processing SPDX file: "+e.getMessage(),e);
		}
		List<String> retval = new ArrayList<String>();
		if (store instanceof TagValueStore) {
			// add in any parser warnings
			retval.addAll(((TagValueStore)store).getWarnings());
		}
		if (SerFileType.JSON.equals(fileType)) {
			try {
				JsonNode spdxJsonSchema = JsonLoader.fromResource(JSON_SCHEMA_RESOURCE);
				final JsonSchema schema = JsonSchemaFactory.byDefault().getJsonSchema(spdxJsonSchema);
				JsonNode spdxDocJson = JsonLoader.fromFile(file);
				ProcessingReport report = schema.validateUnchecked(spdxDocJson, true);
				report.spliterator().forEachRemaining(msg -> {
					JsonNode msgJson = msg.asJson();
					if (!msg.getMessage().contains("$id")) {	// Known warning - this is in the draft 7 spec - perhaps a bug in the validator?
						JsonNode instance = msgJson.findValue("instance");
						String warningStr = msg.getMessage();
						if (Objects.nonNull(instance)) {
							warningStr = warningStr + " for " + instance.toString();
						}
						retval.add(warningStr);
					}
				});
			} catch (IOException e) {
				retval.add("Unable to validate JSON file against schema due to I/O Error");
			} catch (ProcessingException e) {
				retval.add("Unable to validate JSON file against schema due to processing exception");
			}
		}
		List<String> verify = doc.verify();
		
		if (!verify.isEmpty()) {
			for (String verifyMsg:verify) {
				if (!retval.contains(verifyMsg)) {
					retval.add(verifyMsg);
				}
			}
		}
		return retval;
	}

	/**
	 * Verify a tag/value file
	 * @param filePath File path to the SPDX Tag Value file to be verified
	 * @return A list of verification errors - if empty, the SPDX file is valid
	 * @throws SpdxVerificationException Errors where the SPDX Tag Value file can not be parsed or the filename is invalid
	 */
	public static List<String> verifyTagFile(String filePath) throws SpdxVerificationException {
		return verify(filePath, SerFileType.TAG);
	}

	/**
	 * Verify an RDF/XML file
	 * @param filePath File path to the SPDX RDF/XML file to be verified
	 * @return SpdxDocument
	 * @throws SpdxVerificationException Errors where the SPDX RDF/XML file can not be parsed or the filename is invalid
	 */
	public static List<String> verifyRDFFile(String filePath) throws SpdxVerificationException {
		return verify(filePath, SerFileType.RDFXML);
	}
	
	public void usage() {
		System.out.println("Verify filepath [RDFXML|JSON|XLS|XLSX|YAML|TAG]");
		System.out.println("    where filepath is a path to the SPDX file and [RDFXML|JSON|XLS|XLSX|YAML|TAG] is an optional file type - if not present, file type of the to file will be used");
	}
}
