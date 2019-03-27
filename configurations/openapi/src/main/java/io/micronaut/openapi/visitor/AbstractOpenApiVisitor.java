/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.bind.annotation.Bindable;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.servers.ServerVariable;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import javax.validation.constraints.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * Stores the current in progress type.
     */
    private List<String> inProgressSchemas = new ArrayList<>(10);

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


        final String pathString = matchTemplate.toPathString();
        PathItem pathItem = paths.get(pathString);
        if (pathItem == null) {
            pathItem = new PathItem();
            paths.put(pathString, pathItem);
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
                final Map<CharSequence, Object> valueMap = resolveAnnotationValues(context, av);
                newValues.put(key, valueMap);
            } else if (value instanceof AnnotationClassValue) {
                AnnotationClassValue<?> acv = (AnnotationClassValue) value;
                final Optional<? extends Class<?>> type = acv.getType();
                type.ifPresent(aClass -> newValues.put(key, aClass));
            } else if (value != null) {
                if (value.getClass().isArray()) {
                    Object[] a = (Object[]) value;
                    if (ArrayUtils.isNotEmpty(a)) {
                        Object first = a[0];
                        boolean areAnnotationValues = first instanceof AnnotationValue;
                        boolean areClassValues = first instanceof AnnotationClassValue;

                        if (areClassValues) {
                            List<Class> classes = new ArrayList<>(a.length);
                            for (Object o : a) {
                                AnnotationClassValue<?> acv = (AnnotationClassValue) o;
                                acv.getType().ifPresent(classes::add);
                            }
                            newValues.put(key, classes);
                        } else if (areAnnotationValues) {
                            String annotationName = ((AnnotationValue) first).getAnnotationName();
                            if (Content.class.getName().equals(annotationName)) {
                                Map mediaTypes = annotationValueArrayToSubmap(a, "mediaType", context);
                                newValues.put(key, mediaTypes);
                            } else if (Link.class.getName().equals(annotationName) || Header.class.getName().equals(annotationName)) {
                                Map links = annotationValueArrayToSubmap(a, "name", context);
                                newValues.put(key, links);
                            } else if (LinkParameter.class.getName().equals(annotationName)) {
                                Map params = toTupleSubMap(a, "name",  "expression");
                                newValues.put(key, params);
                            } else if (OAuthScope.class.getName().equals(annotationName)) {
                                Map params = toTupleSubMap(a, "name",  "description");
                                newValues.put(key, params);
                            } else if (ApiResponse.class.getName().equals(annotationName)) {
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
                                    final AnnotationValue<?> av = (AnnotationValue<?>) a[0];
                                    final Map<CharSequence, Object> valueMap = resolveAnnotationValues(context, av);
                                    newValues.put(key, toValueMap(valueMap, context));
                                } else {

                                    List list = new ArrayList();
                                    for (Object o : a) {
                                        if (o instanceof AnnotationValue) {
                                            final AnnotationValue<?> av = (AnnotationValue<?>) o;
                                            final Map<CharSequence, Object> valueMap = resolveAnnotationValues(context, av);
                                            list.add(valueMap);
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

    private Map<CharSequence, Object> resolveAnnotationValues(VisitorContext context, AnnotationValue<?> av) {
        final Map<CharSequence, Object> valueMap = toValueMap(av.getValues(), context);
        bindSchemaIfNeccessary(context, av, valueMap);
        final String annotationName = av.getAnnotationName();
        if (Parameter.class.getName().equals(annotationName)) {
            normalizeEnumValues(valueMap, CollectionUtils.mapOf(
                    "in", ParameterIn.class,
                    "style", ParameterStyle.class
            ));
        }
        return valueMap;
    }

    private Map toTupleSubMap(Object[] a, String entryKey, String entryValue) {
        Map params = new LinkedHashMap();
        for (Object o : a) {
            AnnotationValue<?> sv = (AnnotationValue<?>) o;
            final Optional<String> n = sv.get(entryKey, String.class);
            final Optional<String> expr = sv.get(entryValue, String.class);
            if (n.isPresent() && expr.isPresent()) {
                params.put(n.get(), expr.get());
            }
        }
        return params;
    }

    /**
     * Resolves the schema for the given type element.
     *
     * @param openAPI The OpenAPI object
     * @param definingElement The defining element
     * @param type The type element
     * @param context The context
     * @param mediaType An optional media type
     * @return The schema or null if it cannot be resolved
     */
    protected @Nullable Schema resolveSchema(OpenAPI openAPI, @Nullable Element definingElement, ClassElement type, VisitorContext context, @Nullable String mediaType) {
        Schema schema = null;

        if (type instanceof EnumElement) {
            schema = getSchemaDefinition(mediaType, openAPI, context, type, definingElement);
        } else {

            boolean isPublisher = false;

            if (isContainerType(type)) {
                isPublisher = type.isAssignable(Publisher.class.getName()) && !type.isAssignable("reactor.core.publisher.Mono");
                type = type.getFirstTypeArgument().orElse(null);
            }

            if (type != null) {

                String typeName = type.getName();
                PrimitiveType primitiveType = PrimitiveType.fromName(typeName);
                if (ClassUtils.isJavaLangType(typeName)) {
                    schema = getPrimitiveType(typeName);
                } else if (primitiveType != null) {
                    schema = primitiveType.createProperty();
                } else if (type.isAssignable(Map.class.getName())) {
                    schema = new MapSchema();
                    if (type.getTypeArguments().isEmpty()) {
                        schema.setAdditionalProperties(true);
                    } else {
                        Element valueType = type.getTypeArguments().get("V");
                        Schema additionalPropertiesSchema = getPrimitiveType(valueType.getName());
                        if (additionalPropertiesSchema == null) {
                            additionalPropertiesSchema = getSchemaDefinition(mediaType, openAPI, context, valueType, definingElement);
                        }
                        schema.setAdditionalProperties(additionalPropertiesSchema);
                    }
                } else if (type.isIterable()) {
                    Optional<ClassElement> componentType = type.getFirstTypeArgument();
                    if (componentType.isPresent()) {
                        schema = getPrimitiveType(componentType.get().getName());
                    } else {
                        schema = getPrimitiveType(Object.class.getName());
                    }

                    if (schema == null && componentType.isPresent()) {
                        ClassElement componentElement = componentType.get();
                        // we must have a POJO so let's create a component
                        schema = getSchemaDefinition(mediaType, openAPI, context, componentElement, definingElement);
                    }

                    if (schema != null) {
                        schema = arraySchema(schema);
                    }
                } else {
                    schema = getSchemaDefinition(mediaType, openAPI, context, type, definingElement);
                }

            }

            if (schema != null) {
                boolean isStream = MediaType.TEXT_EVENT_STREAM.equals(mediaType) || MediaType.APPLICATION_JSON_STREAM.equals(mediaType);
                if ((!isStream && isPublisher)) {
                    schema = arraySchema(schema);
                }
            }
        }
        return schema;
    }

    /**
     * Resolve the components.
     * @param openAPI The open API
     * @return The components
     */
    protected Components resolveComponents(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = new Components();
            openAPI.setComponents(components);
        }
        return components;
    }


    /**
     * Processes a schema property.
     *  @param context The visitor context
     * @param element The element
     * @param elementType The elemen type
     * @param parentSchema The parent schema
     * @param propertySchema The property schema
     */
    protected void processSchemaProperty(VisitorContext context, Element element, ClassElement elementType, Schema parentSchema, Schema propertySchema) {
        if (propertySchema != null) {
            propertySchema = bindSchemaForElement(context, element, elementType, propertySchema);
            parentSchema.addProperties(element.getName(), propertySchema);
        }
    }

    /**
     * Binds the schema for the given element.
     *
     * @param context The context
     * @param element The element
     * @param elementType The element type
     * @param schemaToBind The schema to bind
     * @return The bound schema
     */
    protected Schema bindSchemaForElement(VisitorContext context, Element element, ClassElement elementType, Schema schemaToBind) {
        AnnotationValue<io.swagger.v3.oas.annotations.media.Schema> schemaAnn = element.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        if (schemaAnn != null) {
            schemaToBind = bindSchemaAnnotationValue(context, element, schemaToBind, schemaAnn);
        }

        Schema finalSchemaToBind = schemaToBind;
        final boolean isIterableOrMap = elementType.isIterable() || elementType.isAssignable(Map.class);

        if (isIterableOrMap) {
            if (element.isAnnotationPresent(NotEmpty.class)) {
                finalSchemaToBind.setMinItems(1);
            }
            element.getValue(Size.class, "min", Integer.class).ifPresent(finalSchemaToBind::setMinItems);
            element.getValue(Size.class, "max", Integer.class).ifPresent(finalSchemaToBind::setMaxItems);
        } else {
            if ("string".equals(finalSchemaToBind.getType())) {
                if (element.isAnnotationPresent(NotEmpty.class) || element.isAnnotationPresent(NotBlank.class)) {
                    finalSchemaToBind.setMinLength(1);
                }
                element.getValue(Size.class, "min", Integer.class).ifPresent(finalSchemaToBind::setMinLength);
                element.getValue(Size.class, "max", Integer.class).ifPresent(finalSchemaToBind::setMaxLength);
            }

            if (element.isAnnotationPresent(Negative.class)) {
                finalSchemaToBind.setMaximum(new BigDecimal(0));
            }
            if (element.isAnnotationPresent(NegativeOrZero.class)) {
                finalSchemaToBind.setMaximum(new BigDecimal(1));
            }
            if (element.isAnnotationPresent(Positive.class)) {
                finalSchemaToBind.setMinimum(new BigDecimal(0));
            }
            if (element.isAnnotationPresent(PositiveOrZero.class)) {
                finalSchemaToBind.setMinimum(new BigDecimal(1));
            }
            element.getValue(Max.class, BigDecimal.class).ifPresent(finalSchemaToBind::setMaximum);
            element.getValue(Min.class, BigDecimal.class).ifPresent(finalSchemaToBind::setMinimum);
            element.getValue(DecimalMax.class, BigDecimal.class).ifPresent(finalSchemaToBind::setMaximum);
            element.getValue(DecimalMin.class, BigDecimal.class).ifPresent(finalSchemaToBind::setMinimum);
            if (element.isAnnotationPresent(Email.class)) {
                finalSchemaToBind.setFormat("email");
            }

            element.findAnnotation(Pattern.class).flatMap((p) -> p.get("regexp", String.class)).ifPresent(finalSchemaToBind::setFormat);
        }



        Optional<String> documentation = element.getDocumentation();
        if (StringUtils.isEmpty(schemaToBind.getDescription())) {
            String doc = documentation.orElse(null);
            if (doc != null) {
                JavadocDescription desc = new JavadocParser().parse(doc);
                schemaToBind.setDescription(desc.getMethodDescription());
            }
        }
        if (element.isAnnotationPresent(Deprecated.class)) {
            schemaToBind.setDeprecated(true);
        }

        final String defaultValue = element.getValue(Bindable.class, "defaultValue", String.class).orElse(null);
        if (defaultValue != null && schemaToBind.getDefault() == null) {
            schemaToBind.setDefault(defaultValue);
        }
        if (element.isAnnotationPresent(Nullable.class)) {
            schemaToBind.setNullable(true);
        }
        return schemaToBind;
    }

    /**
     * Binds the schema for the given element.
     *
     * @param context The context
     * @param element The element
     * @param schemaToBind The schema to bind
     * @param schemaAnn The schema annotation
     * @return The bound schema
     */
    protected Schema bindSchemaAnnotationValue(VisitorContext context, Element element, Schema schemaToBind, AnnotationValue<io.swagger.v3.oas.annotations.media.Schema> schemaAnn) {
        JsonNode schemaJson = toJson(schemaAnn.getValues(), context);
        try {
            schemaToBind = jsonMapper.readerForUpdating(schemaToBind).readValue(schemaJson);
            final String defaultValue = schemaAnn.get("defaultValue", String.class).orElse(null);
            if (StringUtils.isNotEmpty(defaultValue)) {
                schemaToBind.setDefault(defaultValue);
            }
            final String[] allowableValues = schemaAnn.get("allowableValues", String[].class).orElse(null);
            if (ArrayUtils.isNotEmpty(allowableValues)) {
                for (String allowableValue : allowableValues) {
                    schemaToBind.addEnumItemObject(allowableValue);
                }
            }
        } catch (IOException e) {
            context.warn("Error reading Swagger Schema for element [" + element + "]: " + e.getMessage(), element);
        }
        return schemaToBind;
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
                final Schema schema = resolveSchema(openAPI, null, classElement.get(), context, null);
                if (schema != null) {
                    final BeanMap<Schema> beanMap = BeanMap.of(schema);
                    for (Map.Entry<String, Object> e : beanMap.entrySet()) {
                        final Object v = e.getValue();
                        if (v != null) {
                            valueMap.put(e.getKey(), v);
                        }
                    }
                    if (schema.get$ref() != null) {
                        valueMap.put("$ref", schema.get$ref());
                    }
                }
            }
        }
    }

    private Schema getSchemaDefinition(
            @Nullable String mediaType,
            OpenAPI openAPI,
            VisitorContext context,
            Element type,
            @Nullable Element definingElement) {
        // To break the recursion
        if (inProgressSchemas.contains(type.getSimpleName())) {
            return null;
        }
        AnnotationValue<io.swagger.v3.oas.annotations.media.Schema> schemaValue = definingElement != null ? definingElement.getDeclaredAnnotation(io.swagger.v3.oas.annotations.media.Schema.class) : null;
        if (schemaValue == null) {
            schemaValue = type.getDeclaredAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        }
        Schema schema;
        Map<String, Schema> schemas = resolveSchemas(openAPI);
        if (schemaValue != null) {
            String schemaName = schemaValue.get("name", String.class).orElse(computeDefaultSchemaName(definingElement, type));
            inProgressSchemas.add(schemaName);
            schema = schemas.get(schemaName);
            if (schema == null) {
                JsonNode schemaJson = toJson(schemaValue.getValues(), context);
                try {
                    schema = jsonMapper.treeToValue(schemaJson, Schema.class);

                    if (schema != null) {
                        if (schema instanceof ComposedSchema) {
                            final Optional<String[]> allOf = schemaValue.get("allOf", String[].class);
                            if (allOf.isPresent() && allOf.get().length > 0) {
                                final String[] names = allOf.get();
                                List<Schema> schemaList = namesToSchemas(mediaType, openAPI, context, names);

                                ((ComposedSchema) schema).allOf(schemaList);
                            }

                            final Optional<String[]> anyOf = schemaValue.get("anyOf", String[].class);
                            if (anyOf.isPresent() && anyOf.get().length > 0) {
                                final String[] names = anyOf.get();
                                List<Schema> schemaList = namesToSchemas(mediaType, openAPI, context, names);
                                ((ComposedSchema) schema).anyOf(schemaList);
                            }

                            final Optional<String[]> oneof = schemaValue.get("oneOf", String[].class);
                            if (oneof.isPresent() && oneof.get().length > 0) {
                                final String[] names = oneof.get();
                                List<Schema> schemaList = namesToSchemas(mediaType, openAPI, context, names);
                                ((ComposedSchema) schema).oneOf(schemaList);
                            }

                            schema.setType("object");
                        }
                        if (type instanceof EnumElement) {
                            schema.setType("string");
                            schema.setEnum(((EnumElement) type).values());
                        } else if (schema instanceof ObjectSchema || schema instanceof ComposedSchema) {
                            populateSchemaProperties(mediaType, openAPI, context, type, schema);
                        }
                        schema.setName(schemaName);
                        schemas.put(schemaName, schema);
                    }
                } catch (JsonProcessingException e) {
                    context.warn("Error reading Swagger Parameter for element [" + type + "]: " + e.getMessage(), type);
                } finally {
                    inProgressSchemas.remove(schemaName);
                }
            }
        } else {
            final boolean isBasicType = ClassUtils.isJavaBasicType(type.getName());
            final PrimitiveType primitiveType;
            if (isBasicType) {
                primitiveType = ClassUtils.forName(type.getName(), getClass().getClassLoader()).map(PrimitiveType::fromType).orElse(null);
            } else {
                primitiveType = null;
            }
            if (primitiveType != null) {
                return primitiveType.createProperty();
            } else {
                String schemaName = computeDefaultSchemaName(definingElement, type);
                schema = schemas.get(schemaName);
                if (schema == null) {

                    if (type instanceof EnumElement) {
                        schema = new Schema();
                        schema.setName(schemaName);
                        schemas.put(schemaName, schema);

                        schema.setType("string");
                        schema.setEnum(((EnumElement) type).values());
                    } else {
                        if (type instanceof TypedElement) {
                            ClassElement classElement = ((TypedElement) type).getType();
                            Optional<ClassElement> superType = classElement != null ? classElement.getSuperType() : Optional.empty();
                            if (!superType.isPresent()) {
                                schema = new Schema();
                            } else {
                                schema = new ComposedSchema();
                                while (superType.isPresent()) {
                                    final ClassElement superElement = superType.get();

                                    final Schema parentSchema = getSchemaDefinition(mediaType, openAPI, context, superElement, null);
                                    if (parentSchema != null) {
                                        ((ComposedSchema) schema).allOf(Collections.singletonList(parentSchema));
                                    }
                                    superType = superElement.getSuperType();
                                }
                            }
                        } else {
                            schema = new Schema();
                        }
                        schema.setType("object");
                        schema.setName(schemaName);
                        schemas.put(schemaName, schema);

                        populateSchemaProperties(mediaType, openAPI, context, type, schema);
                    }
                }
            }
        }
        if (schema != null) {
            Schema schemaRef = new Schema();
            schemaRef.set$ref("#/components/schemas/" + schema.getName());
            return schemaRef;
        }
        return null;
    }

    private List<Schema> namesToSchemas(@Nullable String mediaType, OpenAPI openAPI, VisitorContext context, String[] names) {
        return Arrays.stream(names).flatMap((Function<String, Stream<Schema>>) className -> {
                                        final Optional<ClassElement> classElement = context.getClassElement(className);
                                        if (classElement.isPresent()) {
                                            final Schema schemaDefinition = getSchemaDefinition(mediaType, openAPI, context, classElement.get(), null);
                                            if (schemaDefinition != null) {
                                                return Stream.of(schemaDefinition);
                                            }
                                        }

                                        return Stream.empty();
                                    }).collect(Collectors.toList());
    }

    private String computeDefaultSchemaName(Element definingElement, Element type) {
        final String metaAnnName = definingElement != null ? definingElement.getAnnotationNameByStereotype(io.swagger.v3.oas.annotations.media.Schema.class).orElse(null) : null;
        if (metaAnnName != null && !io.swagger.v3.oas.annotations.media.Schema.class.getName().equals(metaAnnName)) {
            return NameUtils.getSimpleName(metaAnnName);
        }
        if (type instanceof TypedElement) {
            ClassElement classElement = ((TypedElement) type).getType();
            if (classElement != null) {
                return computeNameWithGenerics(classElement);
            }

        }
        return type.getSimpleName();
    }

    private String computeNameWithGenerics(ClassElement classElement) {
        StringBuilder builder = new StringBuilder(classElement.getSimpleName());
        computeNameWithGenerics(classElement, builder);
        return builder.toString();
    }

    private void computeNameWithGenerics(ClassElement classElement, StringBuilder builder) {
        final Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
        final Iterator<ClassElement> i = typeArguments.values().iterator();
        if (i.hasNext()) {

            builder.append('<');
            while (i.hasNext()) {
                final ClassElement ce = i.next();
                builder.append(ce.getSimpleName());
                computeNameWithGenerics(ce, builder);
                if (i.hasNext()) {
                    builder.append(",");
                }
            }

            builder.append('>');
        }
    }

    private void populateSchemaProperties(String mediaType, OpenAPI openAPI, VisitorContext context, Element type, Schema schema) {
        ClassElement classElement = null;
        if (type instanceof ClassElement) {
            classElement = (ClassElement) type;
        } else if (type instanceof TypedElement) {
            classElement = ((TypedElement) type).getType();
        }

        if (classElement != null) {
            List<PropertyElement> beanProperties = classElement.getBeanProperties();
            processPropertyElements(mediaType, openAPI, context, type, schema, beanProperties);

            final List<FieldElement> publicFields = classElement.getFields(mods -> mods.contains(ElementModifier.PUBLIC) && mods.size() == 1);

            processPropertyElements(mediaType, openAPI, context, type, schema, publicFields);
        }
    }

    private void processPropertyElements(String mediaType, OpenAPI openAPI, VisitorContext context, Element type, Schema schema, List<? extends TypedElement> publicFields) {
        for (TypedElement publicField : publicFields) {
            if (publicField.isAnnotationPresent(JsonIgnore.class) || publicField.isAnnotationPresent(Hidden.class)) {
                continue;
            }

            if (publicField instanceof MemberElement && ((MemberElement) publicField).getDeclaringType().equals(type)) {

                Schema propertySchema = resolveSchema(openAPI, null, publicField.getType(), context, mediaType);

                processSchemaProperty(
                        context,
                        publicField,
                        publicField.getType(),
                        schema,
                        propertySchema
                );
            }
        }
    }

    private Map<String, Schema> resolveSchemas(OpenAPI openAPI) {
        Components components = resolveComponents(openAPI);
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
        Optional<Class> aClass = ClassUtils.getPrimitiveType(typeName);
        if (!aClass.isPresent()) {
            aClass = ClassUtils.forName(typeName, getClass().getClassLoader());
        }

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

    /**
     * Processes {@link io.swagger.v3.oas.annotations.security.SecurityScheme} annotations.
     *
     * @param element The element
     * @param context The visitor context
     */
    protected void processSecuritySchemes(ClassElement element, VisitorContext context) {
        final List<AnnotationValue<io.swagger.v3.oas.annotations.security.SecurityScheme>> values = element.getAnnotationValuesByType(io.swagger.v3.oas.annotations.security.SecurityScheme.class);
        final OpenAPI openAPI = resolveOpenAPI(context);
        for (AnnotationValue<io.swagger.v3.oas.annotations.security.SecurityScheme> securityRequirementAnnotationValue : values) {

            final Optional<String> n = securityRequirementAnnotationValue.get("name", String.class);
            n.ifPresent(name -> {

                final Map<CharSequence, Object> map = toValueMap(securityRequirementAnnotationValue.getValues(), context);
                normalizeEnumValues(map, CollectionUtils.mapOf(
                        "type", SecurityScheme.Type.class, "in", SecurityScheme.In.class
                ));
                final JsonNode jsonNode = toJson(map, context);
                try {
                    final Optional<SecurityScheme> securityRequirement = Optional.of(jsonMapper.treeToValue(jsonNode, SecurityScheme.class));
                    securityRequirement.ifPresent(securityScheme -> {

                        try {
                            securityScheme.setIn(Enum.valueOf(SecurityScheme.In.class, map.get("in").toString().toUpperCase(Locale.ENGLISH)));
                        } catch (Exception e) {
                            // ignore
                        }
                        resolveComponents(openAPI).addSecuritySchemes(name, securityScheme);
                            }
                    );
                } catch (JsonProcessingException e) {
                    context.warn("Error reading Swagger SecurityRequirement for element [" + element + "]: " + e.getMessage(), element);
                }
            });
        }
    }

    /**
     * Normalizes enum values stored in the map.
     *
     * @param paramValues The values
     * @param enumTypes The enum types.
     */
    protected void normalizeEnumValues(Map<CharSequence, Object> paramValues, Map<String, Class<? extends Enum>> enumTypes) {
        for (Map.Entry<String, Class<? extends Enum>> entry : enumTypes.entrySet()) {
            final String name = entry.getKey();
            final Class<? extends Enum> enumType = entry.getValue();
            Object in = paramValues.get(name);
            if (in != null) {
                try {
                    final Enum enumInstance = Enum.valueOf(enumType, in.toString());
                    paramValues.put(name, enumInstance.toString());
                } catch (Exception e) {
                    // ignore
                }
            }

        }
    }
}
