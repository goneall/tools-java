/**
 * Copyright (c) 2020 Source Auditor Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
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
 */
package org.spdx.tools.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.SpdxJsonLDContext;
import org.spdx.library.SpdxConstants;
import org.spdx.library.model.ReferenceType;
import org.spdx.library.model.SpdxElement;
import org.spdx.library.model.SpdxModelFactory;
import org.spdx.library.model.license.AnyLicenseInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Gary O'Neall
 *
 * Converts from RDF/OWL RDF/XML documents to JSON Schema draft 7
 *
 */
public class OwlToJsonSchema extends AbstractOwlRdfConverter {

    private static final String PROP_SPDXREF = "SPDXREF";   //TODO - move this constant to SpdxConstant class
    // JSON Schema string constants
    private static final String JSON_TYPE_STRING = "string";
    private static final String JSON_TYPE_BOOLEAN = "boolean";
    private static final String JSON_TYPE_INTEGER = "integer";
    private static final String JSON_TYPE_OBJECT = "object";
    private static final String JSON_TYPE_ARRAY = "array";
    
    private static final String JSON_RESTRICTION_TYPE = "type";
    private static final String JSON_RESTRICTION_ITEMS = "items";
    private static final String JSON_RESTRICTION_MIN_ITEMS = "minItems";
    private static final String JSON_RESTRICTION_MAXITEMS = "maxItems";
    
    private static final String SCHEMA_VERSION_URI = "http://json-schema.org/draft-07/schema#";
	private static final String RELATIONSHIP_TYPE = SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_RELATIONSHIP;
	private static final String ANNOTATION_TYPE = SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_ANNOTATION;
	static ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private static final Set<String> USES_SPDXIDS;
    
	static {
	    Set<String> spdxids = new HashSet<>();
	    spdxids.add(SpdxConstants.CLASS_SPDX_DOCUMENT);
	    spdxids.add(SpdxConstants.CLASS_SPDX_ELEMENT);
	    spdxids.add(SpdxConstants.CLASS_SPDX_FILE);
	    spdxids.add(SpdxConstants.CLASS_SPDX_ITEM);
	    spdxids.add(SpdxConstants.CLASS_SPDX_PACKAGE);
	    spdxids.add(SpdxConstants.CLASS_SPDX_SNIPPET);
	    USES_SPDXIDS = Collections.unmodifiableSet(spdxids);
	}

	public OwlToJsonSchema(OntModel model) {
		super(model);
	}

	public ObjectNode convertToJsonSchema() {
		ObjectNode root = jsonMapper.createObjectNode();
		root.put("$schema", SCHEMA_VERSION_URI);
		ExtendedIterator<Ontology> ontologyIter = model.listOntologies();
		if (ontologyIter.hasNext()) {
			Ontology ont = ontologyIter.next();
			String ontologyUri = ont.getURI();
			if (Objects.nonNull(ontologyUri)) {
				root.put("$id", ontologyUri);
			}
			String title = ont.getLabel(null);
			if (Objects.nonNull(title)) {
				root.put("title", title);
			}
		}
		root.put(JSON_RESTRICTION_TYPE,JSON_TYPE_OBJECT);
		ObjectNode properties = jsonMapper.createObjectNode();
		ArrayNode required = jsonMapper.createArrayNode();
		OntClass docClass = model.getOntClass(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_SPDX_DOCUMENT);
		Objects.requireNonNull(docClass, "Missing SpdxDocument class in OWL document");
		addClassProperties(docClass, properties, required);
		// Add in the extra properties
		properties.set(SpdxConstants.PROP_DOCUMENT_NAMESPACE, createSimpleTypeSchema(JSON_TYPE_STRING, 
		        "The URI provides an unambiguous mechanism for other SPDX documents to reference SPDX elements within this SPDX document."));
		properties.set(SpdxConstants.PROP_DOCUMENT_DESCRIBES, toArraySchema(createSimpleTypeSchema(JSON_TYPE_STRING, null), 
		        "Packages, files and/or Snippets described by this SPDX document", 0));
        
		OntClass packageClass = model.getOntClass(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_SPDX_PACKAGE);
		Objects.requireNonNull(packageClass, "Missing SPDX Package class in OWL document");
		properties.set("packages", toArrayPropertySchema(packageClass, 0));
		OntClass fileClass = model.getOntClass(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_SPDX_FILE);
		Objects.requireNonNull(fileClass, "Missing SPDX File class in OWL document");
		properties.set("files", toArrayPropertySchema(fileClass, 0));
		OntClass snippetClass = model.getOntClass(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_SPDX_SNIPPET);
		Objects.requireNonNull(snippetClass, "Missing SPDX Snippet class in OWL document");
		properties.set("snippets", toArrayPropertySchema(snippetClass, 0));
		// Add in the relationship class to the top level
		OntClass relationshipClass = model.getOntClass(RELATIONSHIP_TYPE);
		Objects.requireNonNull(relationshipClass, "Missing SPDX Relationship class in OWL document");
		properties.set("relationships", toArrayPropertySchema(relationshipClass, 0));
		// Add in the annotation class to the top level
        OntClass annotationClass = model.getOntClass(ANNOTATION_TYPE);
        Objects.requireNonNull(annotationClass, "Missing SPDX Annotation class in OWL document");
        properties.set("annotations", toArrayPropertySchema(annotationClass, 0));
		root.set("properties", properties);
		root.set("required", required);
		return root;
	}

	/**
	 * @param ontClass
	 * @param min Minimum number of array items
	 * @return JSON Schema of an array of item types represented by the ontClass
	 */
	private JsonNode toArrayPropertySchema(OntClass ontClass, int min) {
		return toArraySchema(ontClassToJsonSchema(ontClass), 
		        checkConvertRenamedPropertyName(ontClass.getLocalName()) + "s referenced in the SPDX document",
		        min);
	}
	
	/**
	 * @param itemSchema Schema for each item
	 * @param description Description for the array
	 * @param min Minimum number of elements for the array
	 * @return JSON Schema of an array of item types
	 */
	private JsonNode toArraySchema(ObjectNode itemSchema, String description, int min) {
	    ObjectNode property = jsonMapper.createObjectNode();
        property.put("description", description);
        property.put(JSON_RESTRICTION_TYPE, JSON_TYPE_ARRAY);
        property.set(JSON_RESTRICTION_ITEMS, itemSchema);
        if (min > 0) {
            property.put(JSON_RESTRICTION_MIN_ITEMS, min);
        }
        return property;
	}

	/**
     * @param ontClass Ontology class
     * @return a schema node representing the object
     */
    private ObjectNode ontClassToJsonSchema(OntClass ontClass) {
        ObjectNode retval = jsonMapper.createObjectNode();
        retval.put(JSON_RESTRICTION_TYPE, JSON_TYPE_OBJECT);
        ObjectNode properties = jsonMapper.createObjectNode();
        ArrayNode required = jsonMapper.createArrayNode();
        if (ontClass.getLocalName().equals(SpdxConstants.CLASS_RELATIONSHIP)) {
            // Need to add the spdxElementId
            properties.set(SpdxConstants.PROP_SPDX_ELEMENTID, createSimpleTypeSchema(JSON_TYPE_STRING, 
                    "Id to which the SPDX element is related"));
            required.add(SpdxConstants.PROP_SPDX_ELEMENTID);
        }
        if (ontClass.getLocalName().equals(SpdxConstants.CLASS_ANNOTATION)) {
            // Need to add in the SPDX ID
            properties.set(PROP_SPDXREF, createSimpleTypeSchema(JSON_TYPE_STRING, 
                    "Id for the SPDX element which has the annotation"));
            required.add(PROP_SPDXREF);
        }
        addClassProperties(ontClass, properties, required);
        if (properties.size() > 0) {
            retval.set("properties", properties);
            if (required.size() > 0) {
                retval.set("required", required);
            }
        }
        return retval;
    }
    
    /**
     * Create a simple schema with just a type and description
     * @param type JSON type
     * @param description description of the property
     * @return JSON schema for a simple property with type and description
     */
    private ObjectNode createSimpleTypeSchema(String type, @Nullable String description) {
        Objects.requireNonNull(type, "Type can not be null");
        ObjectNode retval = jsonMapper.createObjectNode();
        retval.put(JSON_RESTRICTION_TYPE, type);
        if (Objects.nonNull(description) && !description.isEmpty()) {
            retval.put("description", description);
        }
        return retval;
    }

    /**
	 * Adds properties from the RDF ontology class the list jsonSchemaProperties
	 * @param spdxClass RDF ontology class
	 * @param jsonSchemaProperties properties for the top level JSON schema
	 * @param required Array of required properties for the class
	 * @return JSON Schema
	 */
	private void addClassProperties(OntClass spdxClass, ObjectNode jsonSchemaProperties,
	        ArrayNode required) {
        if (USES_SPDXIDS.contains(spdxClass.getLocalName())) {
            required.add(SpdxConstants.SPDX_IDENTIFIER);
            jsonSchemaProperties.set(SpdxConstants.SPDX_IDENTIFIER, 
                    createSimpleTypeSchema(JSON_TYPE_STRING, 
                            "Uniquely identify any element in an SPDX document which may be referenced by other elements."));
        }
		Collection<OntProperty> ontProperties = propertiesFromClassRestrictions(spdxClass);
		for (OntProperty property:ontProperties) {
			if (SKIPPED_PROPERTIES.contains(property.getURI())) {
				continue;
			}
			PropertyRestrictions restrictions = getPropertyRestrictions(spdxClass, property);
			Objects.requireNonNull(restrictions.getTypeUri(), "Missing type for property "+property.getLocalName());
			if (restrictions.getTypeUri().equals(RELATIONSHIP_TYPE) ||
			        restrictions.getTypeUri().equals(ANNOTATION_TYPE)) {
			    // These are included in the outside document are are not properties of the element for the JSON serialization
				continue;
			}
			if (restrictions.isListProperty()) {
			    jsonSchemaProperties.set(MultiFormatStore.propertyNameToCollectionPropertyName(
						checkConvertRenamedPropertyName(property.getLocalName())),
						deriveListPropertySchema(property, restrictions));
			} else {
			    jsonSchemaProperties.set(checkConvertRenamedPropertyName(property.getLocalName()), derivePropertySchema(property, restrictions));
			    if (!restrictions.isOptional()) {
			        required.add(checkConvertRenamedPropertyName(property.getLocalName()));
			    }
			}
		}
	}
	
    /**
     * Derive the schema for a property based on the property restrictions
     * @param property property for the schema
     * @param restrictions OWL restrictions for the property
     * @return property schema for the list represented by the property
     */
    private ObjectNode deriveListPropertySchema(OntProperty property, PropertyRestrictions restrictions) {
        ObjectNode propertySchema = jsonMapper.createObjectNode();
        Statement commentStatement = property.getProperty(commentProperty);
        if (Objects.nonNull(commentStatement) && Objects.nonNull(commentStatement.getObject())
                && commentStatement.getObject().isLiteral()) {
            propertySchema.put("description", commentStatement.getObject().asLiteral().getString());
        }
        addCardinalityRestrictions(propertySchema, restrictions);
        propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_ARRAY);
        propertySchema.set(JSON_RESTRICTION_ITEMS, derivePropertySchema(property, restrictions));
        return propertySchema;
    }

    /**
     * Add any restrictions based on the Ontology cardinality
     * @param propertySchema Property schema
     * @param restrictions Ontology restrictions
     */
    private void addCardinalityRestrictions(ObjectNode propertySchema, PropertyRestrictions restrictions) {
        if (restrictions.getAbsoluteCardinality() > 0) {
            propertySchema.put(JSON_RESTRICTION_MIN_ITEMS, restrictions.getAbsoluteCardinality());
            propertySchema.put(JSON_RESTRICTION_MAXITEMS, restrictions.getAbsoluteCardinality());
        } else {
            if (restrictions.getMinCardinality() > 0) {
                propertySchema.put(JSON_RESTRICTION_MIN_ITEMS, restrictions.getMinCardinality());
            }
            if (restrictions.getMaxCardinality() > 0) {
                propertySchema.put(JSON_RESTRICTION_MAXITEMS, restrictions.getMaxCardinality());
            }
        }
    }

    /**
	 * Derive the schema for a property based on the property restrictions
	 * @param property property for the schema
	 * @param restrictions OWL restrictions for the property
	 * @return property schema for the object represented by the property
	 */
	private ObjectNode derivePropertySchema(OntProperty property, PropertyRestrictions restrictions) {
		ObjectNode propertySchema = jsonMapper.createObjectNode();
		Statement commentStatement = property.getProperty(commentProperty);
		if (Objects.nonNull(commentStatement) && Objects.nonNull(commentStatement.getObject())
				&& commentStatement.getObject().isLiteral()) {
			propertySchema.put("description", commentStatement.getObject().asLiteral().getString());
		}
		if (restrictions.isEnumProperty()) {
			propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_STRING);
			ArrayNode enums = jsonMapper.createArrayNode();
			for (String val:restrictions.getEnumValues()) {
				enums.add(val);
			}
			propertySchema.set("enum", enums);
		} else if (restrictions.getTypeUri().equals("http://www.w3.org/2000/01/rdf-schema#Literal")) {
			propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_STRING);
		} else if (restrictions.getTypeUri().startsWith(SpdxConstants.XML_SCHEMA_NAMESPACE)) {
				// Primitive type
			String primitiveType = restrictions.getTypeUri().substring(SpdxConstants.XML_SCHEMA_NAMESPACE.length());
			Class<? extends Object> primitiveClass = SpdxJsonLDContext.XMLSCHEMA_TYPE_TO_JAVA_CLASS.get(primitiveType);
			Objects.requireNonNull(primitiveClass, "No primitive class found for type "+restrictions.getTypeUri());
			if (Boolean.class.equals(primitiveClass)) {
				propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_BOOLEAN);
			} else if (String.class.equals(primitiveClass)) {
				propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_STRING);
			} else if (Integer.class.equals(primitiveClass)) {
				propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_INTEGER);
			} else {
				throw new RuntimeException("Unknown primitive class "+primitiveType);
			}
		} else if (restrictions.getTypeUri().startsWith(SpdxConstants.SPDX_NAMESPACE)) {
			String spdxType = restrictions.getTypeUri().substring(SpdxConstants.SPDX_NAMESPACE.length());
			Class<? extends Object> clazz = SpdxModelFactory.SPDX_TYPE_TO_CLASS.get(spdxType);
			if (Objects.nonNull(clazz) && (AnyLicenseInfo.class.isAssignableFrom(clazz))
					&& !SpdxConstants.PROP_SPDX_EXTRACTED_LICENSES.equals(checkConvertRenamedPropertyName(property.getLocalName()))) {
				// check for AnyLicenseInfo - these are strings with the exception of the extractedLicensingInfos which are the actual license description
				JsonNode description = propertySchema.get("description");
				if (Objects.isNull(description)) {
					propertySchema.put("description", "License expression");
				} else {
					propertySchema.put("description", "License expression for "+checkConvertRenamedPropertyName(property.getLocalName())+".  "+description.asText());
				}
				propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_STRING);
			} else if (Objects.nonNull(clazz) && SpdxElement.class.isAssignableFrom(clazz)) {
				// check for SPDX Elements - these are strings
				JsonNode description = propertySchema.get("description");
				if (Objects.isNull(description)) {
					propertySchema.put("description", "SPDX ID for "+spdxType);
				} else {
					propertySchema.put("description", "SPDX ID for "+spdxType+".  "+description.asText());
				}
				propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_STRING);
			} else if (Objects.nonNull(clazz) && ReferenceType.class.isAssignableFrom(clazz)) {
				// check for ReferenceType - these are strings URI's and not the full object description
				JsonNode description = propertySchema.get("description");
				if (Objects.nonNull(description)) {
					propertySchema.put("description", description.asText());
				}
				propertySchema.put(JSON_RESTRICTION_TYPE, JSON_TYPE_STRING);
			} else {
				OntClass typeClass = model.getOntClass(restrictions.getTypeUri());
				Objects.requireNonNull(typeClass, "No type class found for "+restrictions.getTypeUri());
				propertySchema = ontClassToJsonSchema(typeClass);
				commentStatement = typeClass.getProperty(commentProperty);
				if (Objects.nonNull(commentStatement) && Objects.nonNull(commentStatement.getObject())
						&& commentStatement.getObject().isLiteral()) {
					// replace the property comment with the class comment
					propertySchema.put("description", commentStatement.getObject().asLiteral().getString());
				}
			}
		} else {
			OntClass typeClass = model.getOntClass(restrictions.getTypeUri());
			commentStatement = typeClass.getProperty(commentProperty);
			if (Objects.nonNull(commentStatement) && Objects.nonNull(commentStatement.getObject())
					&& commentStatement.getObject().isLiteral()) {
				// replace the property comment with the class comment
				propertySchema.put("description", commentStatement.getObject().asLiteral().getString());
			}
			Objects.requireNonNull(typeClass, "No type class found for "+restrictions.getTypeUri());
			propertySchema = ontClassToJsonSchema(typeClass);
		}
		return propertySchema;
	}
}
