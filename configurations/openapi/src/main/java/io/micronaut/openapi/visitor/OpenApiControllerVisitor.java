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
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriMatchVariable;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.visitor.*;
import io.micronaut.openapi.javadoc.JavadocDescription;
import io.micronaut.openapi.javadoc.JavadocParser;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.callbacks.Callback;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A {@link TypeElementVisitor} the builds the Swagger model from Micronaut controllers at compile time.
 *
 * @author graemerocher
 * @since 1.0
 */
@Experimental
public class OpenApiControllerVisitor extends AbstractOpenApiVisitor implements TypeElementVisitor<Controller, HttpMethodMapping> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        processSecuritySchemes(element, context);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        Optional<Class<? extends Annotation>> httpMethodOpt = element.getAnnotationTypeByStereotype(HttpMethodMapping.class);

        if (element.isAnnotationPresent(Hidden.class)) {
            return;
        }

        httpMethodOpt.ifPresent(httpMethodClass -> {
            HttpMethod httpMethod = null;
            try {
                httpMethod = HttpMethod.valueOf(httpMethodClass.getSimpleName().toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                // ignore
            }
            if (httpMethod == null) {
                return;
            }

            UriMatchTemplate matchTemplate = UriMatchTemplate.of(element.getValue(Controller.class, String.class).orElse("/"));
            matchTemplate = matchTemplate.nest(element.getValue(HttpMethodMapping.class, String.class).orElse("/"));

            PathItem pathItem = resolvePathItem(context, matchTemplate);
            OpenAPI openAPI = resolveOpenAPI(context);

            final Optional<AnnotationValue<Operation>> operationAnnotation = element.findAnnotation(Operation.class);
            io.swagger.v3.oas.models.Operation swaggerOperation = operationAnnotation.flatMap(o -> {
                JsonNode jsonNode = toJson(o.getValues(), context);

                try {
                    return Optional.of(jsonMapper.treeToValue(jsonNode, io.swagger.v3.oas.models.Operation.class));
                } catch (Exception e) {
                    context.warn("Error reading Swagger Operation for element [" + element + "]: " + e.getMessage(), element);
                    return Optional.empty();
                }
            }).orElse(new io.swagger.v3.oas.models.Operation());


            readTags(element, swaggerOperation);

            readSecurityRequirements(element, context, swaggerOperation);

            readApiResponses(element, context, swaggerOperation);

            readServers(element, context, swaggerOperation);

            readCallbacks(element, context, swaggerOperation);

            JavadocDescription javadocDescription = element.getDocumentation().map(s -> new JavadocParser().parse(s)).orElse(null);

            if (javadocDescription != null && StringUtils.isEmpty(swaggerOperation.getDescription())) {
                swaggerOperation.setDescription(javadocDescription.getMethodDescription());
            }

            setOperationOnPathItem(pathItem, swaggerOperation, httpMethod);

            if (element.isAnnotationPresent(Deprecated.class)) {
                swaggerOperation.setDeprecated(true);
            }

            if (StringUtils.isEmpty(swaggerOperation.getOperationId())) {
                swaggerOperation.setOperationId(element.getName());
            }

            boolean permitsRequestBody = HttpMethod.permitsRequestBody(httpMethod);

            List<Parameter> swaggerParameters = swaggerOperation.getParameters();
            List<UriMatchVariable> pv = matchTemplate.getVariables();
            Map<String, UriMatchVariable> pathVariables = new LinkedHashMap<>(pv.size()) ;
            for (UriMatchVariable variable : pv) {
                pathVariables.put(variable.getName(), variable);
            }

            String consumesMediaType = element.getValue(Consumes.class, String.class).orElse(MediaType.APPLICATION_JSON);
            ApiResponses responses = swaggerOperation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();

                swaggerOperation.setResponses(responses);

                ApiResponse okResponse = new ApiResponse();

                if (javadocDescription != null) {

                    String returnDescription = javadocDescription.getReturnDescription();
                    okResponse.setDescription(returnDescription);
                }

                ClassElement returnType = element.getReturnType();
                if (returnType.isAssignable(HttpResponse.class)) {
                    returnType = returnType.getFirstTypeArgument().orElse(returnType);
                }
                if (returnType != null) {
                    String mediaType = element.getValue(Produces.class, String.class).orElse(MediaType.APPLICATION_JSON);
                    Content content = buildContent(returnType, mediaType, openAPI, context);
                    okResponse.setContent(content);
                }
                responses.put(ApiResponses.DEFAULT, okResponse);
            }


            boolean hasExistingParameters = CollectionUtils.isNotEmpty(swaggerParameters);
            if (!hasExistingParameters) {
                swaggerParameters = new ArrayList<>();
                swaggerOperation.setParameters(swaggerParameters);
            }

            for (ParameterElement parameter : element.getParameters()) {

                ClassElement parameterType = parameter.getType();
                String parameterName = parameter.getName();
                if (parameterType == null) {
                    continue;
                }

                if (parameter.isAnnotationPresent(Body.class)) {

                    if (permitsRequestBody && swaggerOperation.getRequestBody() == null) {
                        RequestBody requestBody = new RequestBody();
                        if (javadocDescription != null) {

                            CharSequence desc = javadocDescription.getParameters().get(parameterName);
                            if (desc != null) {
                                requestBody.setDescription(desc.toString());
                            }
                        }
                        requestBody.setRequired(!parameter.isAnnotationPresent(Nullable.class) && !parameterType.isAssignable(Optional.class));

                        Content content = buildContent(parameterType, consumesMediaType, openAPI, context);
                        requestBody.setContent(content);
                        swaggerOperation.setRequestBody(requestBody);
                    }
                    continue;
                }

                if (hasExistingParameters) {
                    continue;
                }

                Parameter newParameter = null;

                if (!parameter.hasStereotype(Bindable.class) && pathVariables.containsKey(parameterName)) {
                    UriMatchVariable var = pathVariables.get(parameterName);
                    newParameter = new Parameter();
                    newParameter.setIn(ParameterIn.PATH.toString());
                    final boolean exploded = var.isExploded();
                    if (exploded) {
                        newParameter.setExplode(exploded);
                    }
                } else if (parameter.isAnnotationPresent(Header.class)) {
                    String headerName = parameter.getValue(Header.class, "name", String.class).orElseGet(() -> NameUtils.hyphenate(parameterName));
                    newParameter = new Parameter();
                    newParameter.setIn(ParameterIn.HEADER.toString());
                    newParameter.setName(headerName);
                } else if (parameter.isAnnotationPresent(CookieValue.class)) {
                    String cookieName = parameter.getValue(CookieValue.class, String.class).orElse(parameterName);
                    newParameter = new Parameter();
                    newParameter.setIn(ParameterIn.COOKIE.toString());
                    newParameter.setName(cookieName);
                } else if (parameter.isAnnotationPresent(QueryValue.class)) {
                    String queryVar = parameter.getValue(QueryValue.class, String.class).orElse(parameterName);
                    newParameter = new Parameter();
                    newParameter.setIn(ParameterIn.QUERY.toString());
                    newParameter.setName(queryVar);
                }

                if (parameter.isAnnotationPresent(io.swagger.v3.oas.annotations.Parameter.class)) {
                    AnnotationValue<io.swagger.v3.oas.annotations.Parameter> paramAnn = parameter.findAnnotation(io.swagger.v3.oas.annotations.Parameter.class).orElse(null);

                    if (paramAnn != null) {
                        Map<CharSequence, Object> paramValues = toValueMap(paramAnn.getValues(), context);
                        normalizeEnumValues(paramValues, Collections.singletonMap(
                                "in", ParameterIn.class
                        ));

                        JsonNode jsonNode = jsonMapper.valueToTree(paramValues);

                        if (newParameter == null) {
                            try {
                                newParameter = jsonMapper.treeToValue(jsonNode, Parameter.class);
                            } catch (Exception e) {
                                context.warn("Error reading Swagger Parameter for element [" + parameter + "]: " + e.getMessage(), parameter);
                            }
                        } else {
                            try {
                                Parameter v = jsonMapper.treeToValue(jsonNode, Parameter.class);
                                if (v != null) {
                                    // horrible hack because Swagger ParameterDeserializer breaks updating existing objects
                                    BeanMap<Parameter> beanMap = BeanMap.of(v);
                                    BeanMap<Parameter> target = BeanMap.of(newParameter);
                                    for (CharSequence name : paramValues.keySet()) {
                                        Object o = beanMap.get(name.toString());
                                        target.put(name.toString(), o);
                                    }
                                } else {
                                    BeanMap<Parameter> target = BeanMap.of(newParameter);
                                    for (CharSequence name : paramValues.keySet()) {
                                        Object o = paramValues.get(name.toString());
                                        try {
                                            target.put(name.toString(), o);
                                        } catch (Exception e) {
                                            // ignore
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                context.warn("Error reading Swagger Parameter for element [" + parameter + "]: " + e.getMessage(), parameter);
                            }
                        }
                    }
                }

                if (newParameter != null) {

                    if (StringUtils.isEmpty(newParameter.getName())) {
                        newParameter.setName(parameterName);
                    }

                    newParameter.setRequired(!parameter.isAnnotationPresent(Nullable.class));
                    // calc newParameter.setExplode();
                    if (javadocDescription != null) {

                        CharSequence desc = javadocDescription.getParameters().get(parameterName);
                        if (desc != null) {
                            newParameter.setDescription(desc.toString());
                        }
                    }
                    swaggerParameters.add(newParameter);

                    Schema schema = newParameter.getSchema();
                    if (schema == null) {
                        schema = resolveSchema(openAPI, parameterType, context, consumesMediaType);
                    }

                    if (schema != null) {
                        bindSchemaForElement(context, parameter, parameter.getType(), schema);
                        newParameter.setSchema(schema);
                    }
                }
            }

            if (HttpMethod.requiresRequestBody(httpMethod) && swaggerOperation.getRequestBody() == null) {
                List<ParameterElement> bodyParameters = Arrays.stream(element.getParameters()).filter(p -> !pathVariables.containsKey(p.getName()) && !p.isAnnotationPresent(Bindable.class)).collect(Collectors.toList());
                if (!bodyParameters.isEmpty()) {

                    RequestBody requestBody = new RequestBody();
                    Content content = new Content();
                    io.swagger.v3.oas.models.media.MediaType mt = new io.swagger.v3.oas.models.media.MediaType();
                    ObjectSchema schema = new ObjectSchema();
                    for (ParameterElement parameter : bodyParameters) {
                        if (parameter.isAnnotationPresent(JsonIgnore.class) || parameter.isAnnotationPresent(Hidden.class)) {
                            continue;
                        }
                        Schema propertySchema = resolveSchema(openAPI, parameter.getType(), context, consumesMediaType);

                        processSchemaProperty(context, parameter, parameter.getType(), schema, propertySchema);

                        propertySchema.setNullable(parameter.isAnnotationPresent(Nullable.class));
                        if (javadocDescription != null && StringUtils.isEmpty(propertySchema.getDescription())) {
                            CharSequence doc = javadocDescription.getParameters().get(parameter.getName());
                            if (doc != null) {
                                propertySchema.setDescription(doc.toString());
                            }
                        }
                    }
                    mt.setSchema(schema);
                    content.addMediaType(consumesMediaType, mt);

                    requestBody.setContent(content);
                    requestBody.setRequired(true);
                    swaggerOperation.setRequestBody(requestBody);
                }
            }
        });
    }

    private void setOperationOnPathItem(PathItem pathItem, io.swagger.v3.oas.models.Operation swaggerOperation, HttpMethod httpMethod) {
        switch (httpMethod) {
            case GET:
                pathItem.get(swaggerOperation);
            break;
            case POST:
                pathItem.post(swaggerOperation);
            break;
            case PUT:
                pathItem.put(swaggerOperation);
            break;
            case PATCH:
                pathItem.patch(swaggerOperation);
            break;
            case DELETE:
                pathItem.delete(swaggerOperation);
            break;
            case HEAD:
                pathItem.head(swaggerOperation);
            break;
            case OPTIONS:
                pathItem.options(swaggerOperation);
            break;
            case TRACE:
                pathItem.trace(swaggerOperation);
            break;
            default:
                // unprocessable
        }
    }

    private void readApiResponses(MethodElement element, VisitorContext context, io.swagger.v3.oas.models.Operation swaggerOperation) {
        List<AnnotationValue<io.swagger.v3.oas.annotations.responses.ApiResponse>> responseAnnotations = element.getAnnotationValuesByType(io.swagger.v3.oas.annotations.responses.ApiResponse.class);
        if (CollectionUtils.isNotEmpty(responseAnnotations)) {
            ApiResponses apiResponses = new ApiResponses();
            for (AnnotationValue<io.swagger.v3.oas.annotations.responses.ApiResponse> r : responseAnnotations) {

                JsonNode jn = toJson(r.getValues(), context);
                try {
                    Optional<ApiResponse> newResponse = Optional.of(jsonMapper.treeToValue(jn, ApiResponse.class));
                    newResponse.ifPresent(apiResponse -> {
                        String name = r.get("responseCode", String.class).orElse("default");
                        apiResponses.put(name, apiResponse);
                    });
                } catch (Exception e) {
                    context.warn("Error reading Swagger ApiResponses for element [" + element + "]: " + e.getMessage(), element);
                }
            }
            swaggerOperation.setResponses(apiResponses);
        }
    }

    private void readSecurityRequirements(MethodElement element, VisitorContext context, io.swagger.v3.oas.models.Operation swaggerOperation) {
        List<AnnotationValue<io.swagger.v3.oas.annotations.security.SecurityRequirement>> securityAnnotations = element.getAnnotationValuesByType(io.swagger.v3.oas.annotations.security.SecurityRequirement.class);
        if (CollectionUtils.isNotEmpty(securityAnnotations)) {
            for (AnnotationValue<io.swagger.v3.oas.annotations.security.SecurityRequirement> r : securityAnnotations) {
                JsonNode jn = toJson(r.getValues(), context);
                try {
                    Optional<SecurityRequirement> newRequirement = Optional.of(jsonMapper.treeToValue(jn, SecurityRequirement.class));
                    newRequirement.ifPresent(swaggerOperation::addSecurityItem);
                } catch (Exception e) {
                    context.warn("Error reading Swagger SecurityRequirement for element [" + element + "]: " + e.getMessage(), element);
                }
            }
        }
    }

    private void readServers(MethodElement element, VisitorContext context, io.swagger.v3.oas.models.Operation swaggerOperation) {
        List<AnnotationValue<io.swagger.v3.oas.annotations.servers.Server>> serverAnnotations = element.getAnnotationValuesByType(io.swagger.v3.oas.annotations.servers.Server.class);
        if (CollectionUtils.isNotEmpty(serverAnnotations)) {
            for (AnnotationValue<io.swagger.v3.oas.annotations.servers.Server> r : serverAnnotations) {
                JsonNode jn = toJson(r.getValues(), context);
                try {
                    Optional<Server> newRequirement = Optional.of(jsonMapper.treeToValue(jn, Server.class));
                    newRequirement.ifPresent(swaggerOperation::addServersItem);
                } catch (Exception e) {
                    context.warn("Error reading Swagger Server for element [" + element + "]: " + e.getMessage(), element);
                }
            }
        }
    }

    private void readCallbacks(MethodElement element, VisitorContext context, io.swagger.v3.oas.models.Operation swaggerOperation) {
        List<AnnotationValue<Callback>> callbackAnnotations = element.getAnnotationValuesByType(Callback.class);
        if (CollectionUtils.isNotEmpty(callbackAnnotations)) {
            for (AnnotationValue<Callback> callbackAnn : callbackAnnotations) {
                final Optional<String> n = callbackAnn.get("name", String.class);
                n.ifPresent(callbackName -> {

                    final Optional<String> expr = callbackAnn.get("callbackUrlExpression", String.class);
                    if (expr.isPresent()) {

                        final String callbackUrl = expr.get();

                        final List<AnnotationValue<Operation>> operations = callbackAnn.getAnnotations("operation", Operation.class);
                        if (CollectionUtils.isEmpty(operations)) {
                            Map<String, io.swagger.v3.oas.models.callbacks.Callback> callbacks = initCallbacks(swaggerOperation);
                            final io.swagger.v3.oas.models.callbacks.Callback c = new io.swagger.v3.oas.models.callbacks.Callback();
                            c.addPathItem(callbackUrl, new PathItem());
                            callbacks.put(callbackName, c);
                        } else {
                            final PathItem pathItem = new PathItem();
                            for (AnnotationValue<Operation> operation : operations) {
                                final Optional<HttpMethod> operationMethod = operation.get("method", HttpMethod.class);
                                operationMethod.ifPresent(httpMethod -> {
                                    JsonNode jsonNode = toJson(operation.getValues(), context);

                                    try {
                                        final Optional<io.swagger.v3.oas.models.Operation> op = Optional.of(jsonMapper.treeToValue(jsonNode, io.swagger.v3.oas.models.Operation.class));
                                        op.ifPresent(operation1 -> setOperationOnPathItem(pathItem, operation1, httpMethod));
                                    } catch (Exception e) {
                                        context.warn("Error reading Swagger Operation for element [" + element + "]: " + e.getMessage(), element);
                                    }
                                });
                            }
                            Map<String, io.swagger.v3.oas.models.callbacks.Callback> callbacks = initCallbacks(swaggerOperation);
                            final io.swagger.v3.oas.models.callbacks.Callback c = new io.swagger.v3.oas.models.callbacks.Callback();
                            c.addPathItem(callbackUrl, pathItem);
                            callbacks.put(callbackName, c);

                        }

                    } else {
                        final Components components = resolveComponents(resolveOpenAPI(context));
                        final Map<String, io.swagger.v3.oas.models.callbacks.Callback> callbackComponents = components.getCallbacks();
                        if (callbackComponents != null && callbackComponents.containsKey(callbackName)) {
                            Map<String, io.swagger.v3.oas.models.callbacks.Callback> callbacks = initCallbacks(swaggerOperation);
                            final io.swagger.v3.oas.models.callbacks.Callback callbackRef = new io.swagger.v3.oas.models.callbacks.Callback();
                            callbackRef.set$ref("#/components/callbacks/" + callbackName);
                            callbacks.put(callbackName, callbackRef);
                        }
                    }
                });

            }
        }
    }

    private Map<String, io.swagger.v3.oas.models.callbacks.Callback> initCallbacks(io.swagger.v3.oas.models.Operation swaggerOperation) {
        Map<String, io.swagger.v3.oas.models.callbacks.Callback> callbacks = swaggerOperation.getCallbacks();
        if (callbacks == null) {
            callbacks = new LinkedHashMap<>();
            swaggerOperation.setCallbacks(callbacks);
        }
        return callbacks;
    }

    private void readTags(MethodElement element, io.swagger.v3.oas.models.Operation swaggerOperation) {
        List<AnnotationValue<Tag>> tagAnnotations = element.getAnnotationValuesByType(Tag.class);
        if (CollectionUtils.isNotEmpty(tagAnnotations)) {
            for (AnnotationValue<Tag> r : tagAnnotations) {
                r.get("name", String.class).ifPresent(swaggerOperation::addTagsItem);
            }
        }
    }

    private Content buildContent(ClassElement type, String mediaType, OpenAPI openAPI, VisitorContext context) {
        Content content = new Content();
        io.swagger.v3.oas.models.media.MediaType mt = new io.swagger.v3.oas.models.media.MediaType();
        mt.setSchema(resolveSchema(openAPI, type, context, mediaType));
        content.addMediaType(mediaType, mt);
        return content;
    }

}
