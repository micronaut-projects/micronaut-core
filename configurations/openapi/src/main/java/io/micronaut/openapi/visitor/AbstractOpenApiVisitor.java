/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.openapi.visitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.openapi.javadoc.JavadocDescription;
import io.micronaut.openapi.javadoc.JavadocParser;
import io.micronaut.openapi.util.Yaml;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.PrimitiveType;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.ServerVariable;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Abstract base class for OpenAPI visitors.
 *
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractOpenApiVisitor  {

    static final String ATTR_TEST_MODE = "io.micronaut.OPENAPI_TEST";
    static final String ATTR_OPENAPI = "io.micronaut.OPENAPI";
    static OpenAPI testReference;

    /**
     * The JSON mapper.
     */
    ObjectMapper jsonMapper = Json.mapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    /**
     * The YAML mapper.
     */
    ObjectMapper yamlMapper = Yaml.mapper();

    /**
     * Convert the given map to a JSON node.
     *
     * @param values The values
     * @param context The visitor context
     * @return The node
     */
    JsonNode toJson(Map<CharSequence, Object> values, VisitorContext context) {
        Map<CharSequence, Object> newValues = toValueMap(values, context);
        return jsonMapper.valueToTree(newValues);
    }

    /**
     * Resolve the PathItem for the given {@link UriMatchTemplate}.
     *
     * @param context The context
     * @param matchTemplate The match template
     * @return The {@link PathItem}
     */
    PathItem resolvePathItem(VisitorContext context, UriMatchTemplate matchTemplate) {
        OpenAPI openAPI = resolveOpenAPI(context);
        Paths paths = openAPI.getPaths();
        if (paths == null) {
            paths = new Paths();
            openAPI.setPaths(paths);
        }


        PathItem pathItem = paths.get(matchTemplate.toString());
        if (pathItem == null) {
            pathItem = new PathItem();
            paths.put(matchTemplate.toString(), pathItem);
        }
        return pathItem;
    }

    /**
     * Resolve the {@link OpenAPI} instance.
     *
     * @param context The context
     * @return The {@link OpenAPI} instance
     */
    OpenAPI resolveOpenAPI(VisitorContext context) {
        OpenAPI openAPI = context.get(ATTR_OPENAPI, OpenAPI.class).orElse(null);
        if (openAPI == null) {
            openAPI = new OpenAPI();
            context.put(ATTR_OPENAPI, openAPI);
            if (Boolean.getBoolean(ATTR_TEST_MODE)) {
                testReference = openAPI;
            }
        }
        return openAPI;
    }

    /**
     * Convert the values to a map.
     * @param values The values
     * @param context The visitor context
     * @return The map
     */
    protected Map<CharSequence, Object> toValueMap(Map<CharSequence, Object> values, VisitorContext context) {
        Map<CharSequence, Object> newValues = new HashMap<>(values.size());
        for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
            CharSequence key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof AnnotationValue) {
                AnnotationValue<?> av = (AnnotationValue<?>) value;
                final Map<CharSequence, Object> valueMap = toValueMap(av.getValues(), context);
                bindSchemaIfNeccessary(context, av, valueMap);
                newValues.put(key, valueMap);
            } else if (value != null) {
                if (value.getClass().isArray()) {
                    Object[] a = (Object[]) value;
                    if (ArrayUtils.isNotEmpty(a)) {
                        Object first = a[0];
                        boolean areAnnotationValues = first instanceof AnnotationValue;

                        if (areAnnotationValues) {
                            String annotationName = ((AnnotationValue) first).getAnnotationName();
                            if (Content.class.getName().equals(annotationName)) {
                                Map mediaTypes = annotationValueArrayToSubmap(a, "mediaType", context);
                                newValues.put(key, mediaTypes);
                            } else if (Link.class.getName().equals(annotationName) || Header.class.getName().equals(annotationName)) {
                                Map links = annotationValueArrayToSubmap(a, "name", context);
                                newValues.put(key, links);
                            } else if (LinkParameter.class.getName().equals(annotationName)) {
                                Map params = new LinkedHashMap();
                                for (Object o : a) {
                                    AnnotationValue<LinkParameter> sv = (AnnotationValue<LinkParameter>) o;
                                    final Optional<String> n = sv.get("name", String.class);
                                    final Optional<String> expr = sv.get("expression", String.class);
                                    if (n.isPresent() && expr.isPresent()) {
                                        params.put(n.get(), expr.get());
                                    }
                                }
                                newValues.put(key, params);
                            }
                            else if (ApiResponse.class.getName().equals(annotationName)) {
                                Map responses = new LinkedHashMap();
                                for (Object o : a) {
                                    AnnotationValue<ApiResponse> sv = (AnnotationValue<ApiResponse>) o;
                                    String name = sv.get("responseCode", String.class).orElse("default");
                                    Map<CharSequence, Object> map = toValueMap(sv.getValues(), context);
                                    responses.put(name, map);
                                }
                                newValues.put(key, responses);
                            } else if (ServerVariable.class.getName().equals(annotationName)) {
                                Map variables = new LinkedHashMap();
                                for (Object o : a) {
                                    AnnotationValue<ServerVariable> sv = (AnnotationValue<ServerVariable>) o;
                                    Optional<String> n = sv.get("name", String.class);
                                    n.ifPresent(name -> {
                                        Map<CharSequence, Object> map = toValueMap(sv.getValues(), context);
                                        Object dv = map.get("defaultValue");
                                        if (dv != null) {
                                            map.put("default", dv);
                                        }
                                        variables.put(name, map);
                                    });
                                }
                                newValues.put(key, variables);
                            } else {
                                if (a.length == 1) {
                                    newValues.put(key, toValueMap(((AnnotationValue<?>) a[0]).getValues(), context));
                                } else {

                                    List list = new ArrayList();
                                    for (Object o : a) {
                                        if (o instanceof AnnotationValue) {
                                            list.add(toValueMap(((AnnotationValue<?>) o).getValues(), context));
                                        } else {
                                            list.add(o);
                                        }
                                    }
                                    newValues.put(key, list);
                                }
                            }
                        } else {
                            newValues.put(key, value);
                        }
                    }
                } else {
                    newValues.put(key, value);
                }
            }
        }
        return newValues;
    }

    /**
     * Resolves the schema for the given type element.
     *
     * @param openAPI The OpenAPI object
     * @param type The type element
     * @param context The context
     * @param mediaType An optional media type
     * @return The schema or null if it cannot be resolved
     */
    protected @Nullable Schema resolveSchema(OpenAPI openAPI, ClassElement type, VisitorContext context, @Nullable String mediaType) {
        Schema schema = null;

        if (type instanceof EnumElement) {
            schema = getSchemaDefinition(mediaType, openAPI, context, type);
        } else {

            boolean isPublisher = false;

            if (isContainerType(type)) {
                isPublisher = type.isAssignable(Publisher.class.getName()) && !type.isAssignable("reactor.core.publisher.Mono");
                type = type.getFirstTypeArgument().orElse(null);
            }

            if (type != null) {

                String typeName = type.getName();
                if (ClassUtils.isJavaLangType(typeName)) {
                    schema = getPrimitiveType(typeName);
                } else if (type.isIterable()) {
                    Optional<ClassElement> componentType = type.getFirstTypeArgument();
                    if (componentType.isPresent()) {
                        schema = getPrimitiveType(componentType.get().getName());
                    } else {
                        schema = getPrimitiveType(Object.class.getName());
                    }

                    if (schema != null) {
                        schema = arraySchema(schema);
                    } else if (componentType.isPresent()) {
                        ClassElement componentElement = componentType.get();
                        // we must have a POJO so let's create a component
                        schema = getSchemaDefinition(mediaType, openAPI, context, componentElement);
                    }
                } else {
                    schema = getSchemaDefinition(mediaType, openAPI, context, type);
                }

            }

            if (schema != null) {
                boolean isStream = MediaType.TEXT_EVENT_STREAM.equals(mediaType) || MediaType.APPLICATION_JSON_STREAM.equals(mediaType);
                if ((!isStream && isPublisher) || type.isIterable()) {
                    schema = arraySchema(schema);
                }
            }
        }
        return schema;
    }

    /**
     * Processes a schema property
     *
     * @param context The visitor context
     * @param element The element
     * @param parentSchema The parent schema
     * @param propertySchema The property schema
     */
    protected void processSchemaProperty(VisitorContext context, Element element, Schema parentSchema, Schema propertySchema) {
        if (propertySchema != null) {
            AnnotationValue<io.swagger.v3.oas.annotations.media.Schema> schemaAnn = element.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
            if (schemaAnn != null) {
                JsonNode schemaJson = toJson(schemaAnn.getValues(), context);
                try {
                    propertySchema = jsonMapper.readerForUpdating(propertySchema).readValue(schemaJson);
                } catch (IOException e) {
                    context.warn("Error reading Swagger Schema for element [" + element + "]: " + e.getMessage(), element);
                }
            }

            Optional<String> documentation = element.getDocumentation();
            if (StringUtils.isEmpty(propertySchema.getDescription())) {
                String doc = documentation.orElse(null);
                if (doc != null) {
                    JavadocDescription desc = new JavadocParser().parse(doc);
                    propertySchema.setDescription(desc.getMethodDescription());
                }
            }
            if (element.isAnnotationPresent(Deprecated.class)) {
                propertySchema.setDeprecated(true);
            }
            propertySchema.setNullable(element.isAnnotationPresent(Nullable.class));
            parentSchema.addProperties(element.getName(), propertySchema);
        }
    }

    private Map annotationValueArrayToSubmap(Object[] a, String classifier, VisitorContext context) {
        Map mediaTypes = new LinkedHashMap();
        for (Object o : a) {
            AnnotationValue<?> sv = (AnnotationValue<?>) o;
            String name = sv.get(classifier, String.class).orElse(null);
            if (name != null) {
                Map<CharSequence, Object> map = toValueMap(sv.getValues(), context);
                mediaTypes.put(name, map);
            }
        }
        return mediaTypes;
    }

    private void bindSchemaIfNeccessary(VisitorContext context, AnnotationValue<?> av, Map<CharSequence, Object> valueMap) {
        final Optional<String> impl = av.get("implementation", String.class);
        if (io.swagger.v3.oas.annotations.media.Schema.class.getName().equals(av.getAnnotationName()) && impl.isPresent()) {
            final String className = impl.get();
            final Optional<ClassElement> classElement = context.getClassElement(className);
            final OpenAPI openAPI = resolveOpenAPI(context);
            if (classElement.isPresent()) {
                final Schema schema = resolveSchema(openAPI, classElement.get(), context, null);
                if (schema != null) {
                    final BeanMap<Schema> beanMap = BeanMap.of(schema);
                    for (Map.Entry<String, Object> e : beanMap.entrySet()) {
                        final Object v = e.getValue();
                        if (v != null) {
                            valueMap.put(e.getKey(), v);
                        }
                    }
                }
            }
        }
    }

    private Schema getSchemaDefinition(@Nullable String mediaType, OpenAPI openAPI, VisitorContext context, Element type) {
        AnnotationValue<io.swagger.v3.oas.annotations.media.Schema> schemaValue = type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        Schema schema;
        Map<String, Schema> schemas = resolveSchemas(openAPI);
        if (schemaValue != null) {
            String schemaName = schemaValue.get("name", String.class).orElse(NameUtils.getSimpleName(type.getName()));
            schema = schemas.get(schemaName);
            if (schema == null) {
                JsonNode schemaJson = toJson(schemaValue.getValues(), context);
                try {
                    schema = jsonMapper.treeToValue(schemaJson, Schema.class);

                    if (schema != null) {
                        if (type instanceof EnumElement) {
                            schema.setType("string");
                            schema.setEnum(((EnumElement) type).values());
                        } else {
                            populateSchemaProperties(mediaType, openAPI, context, type, schema);
                        }
                        schema.setName(schemaName);
                        schemas.put(schemaName, schema);
                    }
                } catch (JsonProcessingException e) {
                    context.warn("Error reading Swagger Parameter for element [" + type + "]: " + e.getMessage(), type);
                }
            }
        } else {
            String schemaName = NameUtils.getSimpleName(type.getName());
            schema = schemas.get(schemaName);
            if (schema == null) {
                schema = new Schema();
                if (type instanceof EnumElement) {
                    schema.setType("string");
                    schema.setEnum(((EnumElement) type).values());
                } else {
                    schema.setType("object");
                    populateSchemaProperties(mediaType, openAPI, context, type, schema);
                }
                schema.setName(schemaName);
                schemas.put(schemaName, schema);
            }
        }
        if (schema != null) {
            Schema schemaRef = new Schema();
            schemaRef.set$ref("#/components/schemas/" + schema.getName());
            return schemaRef;
        }
        return null;
    }

    private void populateSchemaProperties(String mediaType, OpenAPI openAPI, VisitorContext context, Element type, Schema schema) {
        ClassElement classElement = null;
        if (type instanceof ClassElement) {
            classElement = (ClassElement) type;
        } else if (type instanceof PropertyElement) {
            classElement = ((PropertyElement) type).getType();
        } else if (type instanceof ParameterElement) {
            classElement = ((ParameterElement) type).getType();
        }
        if (classElement != null) {
            List<PropertyElement> beanProperties = classElement.getBeanProperties();
            for (PropertyElement beanProperty : beanProperties) {
                if (beanProperty.isAnnotationPresent(JsonIgnore.class) || beanProperty.isAnnotationPresent(Hidden.class)) {
                    continue;
                }
                Schema propertySchema = resolveSchema(openAPI, beanProperty.getType(), context, mediaType);

                processSchemaProperty(context, beanProperty, schema, propertySchema);

            }
        }
    }

    private Map<String, Schema> resolveSchemas(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = new Components();
            openAPI.setComponents(components);
        }
        Map<String, Schema> schemas = components.getSchemas();
        if (schemas == null) {
            schemas = new LinkedHashMap<>();
            components.setSchemas(schemas);
        }
        return schemas;
    }

    private ArraySchema arraySchema(Schema schema) {
        if (schema == null) {
            return null;
        }
        ArraySchema arraySchema = new ArraySchema();
        arraySchema.setItems(schema);
        return arraySchema;
    }

    private Schema getPrimitiveType(String typeName) {
        Schema schema = null;
        Optional<Class> aClass = ClassUtils.forName(typeName, getClass().getClassLoader());
        if (aClass.isPresent()) {
            Class concreteType = aClass.get();
            Class wrapperType = ReflectionUtils.getWrapperType(concreteType);

            PrimitiveType primitiveType = PrimitiveType.fromType(wrapperType);
            if (primitiveType != null) {
                schema = primitiveType.createProperty();
            }
        }
        return schema;
    }

    private boolean isContainerType(ClassElement type) {
        return CollectionUtils.setOf(
                Optional.class.getName(),
                Future.class.getName(),
                Publisher.class.getName(),
                "io.reactivex.Single",
                "io.reactivex.Observable",
                "io.reactivex.Maybe"
        ).stream().anyMatch(type::isAssignable);
    }
}
