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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.visitor.*;
import io.micronaut.openapi.javadoc.JavadocDescription;
import io.micronaut.openapi.javadoc.JavadocParser;
import io.swagger.v3.core.util.PrimitiveType;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.Future;

/**
 * A {@link TypeElementVisitor} the builds the Swagger model from Micronaut controllers at compile time.
 *
 * @author graemerocher
 * @since 1.0
 */
@Experimental
public class OpenApiControllerVisitor extends AbstractOpenApiVisitor implements TypeElementVisitor<Controller, HttpMethodMapping> {

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        Optional<Class<? extends Annotation>> httpMethodOpt = element.getAnnotationTypeByStereotype(HttpMethodMapping.class);

        if (element.isAnnotationPresent(Hidden.class)) {
            return;
        }

        httpMethodOpt.ifPresent(httpMethodClass -> {

            UriMatchTemplate matchTemplate = UriMatchTemplate.of(element.getValue(Controller.class, String.class).orElse("/"));
            matchTemplate = matchTemplate.nest(element.getValue(HttpMethodMapping.class, String.class).orElse("/"));

            PathItem pathItem = resolvePathItem(context, matchTemplate);

            io.swagger.v3.oas.models.Operation swaggerOperation = element.findAnnotation(Operation.class).flatMap(o -> {
                JsonNode jsonNode = toJson(o.getValues());

                try {
                    return Optional.of(jsonMapper.treeToValue(jsonNode, io.swagger.v3.oas.models.Operation.class));
                } catch (JsonProcessingException e) {
                    context.warn("Error reading Swagger Operation for element [" + element + "]: " + e.getMessage(), element);
                    return Optional.empty();
                }
            }).orElse(new io.swagger.v3.oas.models.Operation());


            List<AnnotationValue<io.swagger.v3.oas.annotations.responses.ApiResponse>> responseAnnotations = element.getAnnotationValuesByType(io.swagger.v3.oas.annotations.responses.ApiResponse.class);
            if (CollectionUtils.isNotEmpty(responseAnnotations)) {
                ApiResponses apiResponses = new ApiResponses();
                for (AnnotationValue<io.swagger.v3.oas.annotations.responses.ApiResponse> r : responseAnnotations) {

                    JsonNode jn = toJson(r.getValues());
                    try {
                        Optional<ApiResponse> newResponse = Optional.of(jsonMapper.treeToValue(jn, ApiResponse.class));
                        newResponse.ifPresent(apiResponse -> {
                            String name = r.get("responseCode", String.class).orElse("default");
                            apiResponses.put(name, apiResponse);
                        });
                    } catch (JsonProcessingException e) {
                        context.warn("Error reading Swagger ApiResponses for element [" + element + "]: " + e.getMessage(), element);
                    }
                }
                swaggerOperation.setResponses(apiResponses);
            }

            HttpMethod httpMethod = HttpMethod.valueOf(httpMethodClass.getSimpleName().toUpperCase(Locale.ENGLISH));
            JavadocDescription javadocDescription = element.getDocumentation().map(s -> new JavadocParser().parse(s)).orElse(null);

            if (javadocDescription != null && StringUtils.isEmpty(swaggerOperation.getDescription())) {
                swaggerOperation.setDescription(javadocDescription.getMethodDescription());
            }

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
                    return;
            }

            if (element.isAnnotationPresent(Deprecated.class)) {
                swaggerOperation.setDeprecated(true);
            }

            if (StringUtils.isEmpty(swaggerOperation.getOperationId())) {
                swaggerOperation.setOperationId(element.getName());
            }

            boolean permitsRequestBody = HttpMethod.permitsRequestBody(httpMethod);

            List<Parameter> swaggerParameters = swaggerOperation.getParameters();
            List<String> pathVariables = matchTemplate.getVariables();

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
                if (returnType != null) {
                    String mediaType = element.getValue(Produces.class, String.class).orElse(MediaType.APPLICATION_JSON);
                    Content content = buildContent(returnType, mediaType);
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

                        Content content = buildContent(parameterType, consumesMediaType);
                        requestBody.setContent(content);
                        swaggerOperation.setRequestBody(requestBody);
                    }
                    continue;
                }

                if (hasExistingParameters) {
                    continue;
                }

                Parameter newParameter = null;

                if (parameter.isAnnotationPresent(io.swagger.v3.oas.annotations.Parameter.class)) {
                    Optional<Parameter> opt = parameter.findAnnotation(io.swagger.v3.oas.annotations.Parameter.class).flatMap(o -> {
                        Map<CharSequence, Object> paramValues = toValueMap(o.getValues());
                        Object in = paramValues.get("in");
                        if (in != null) {
                            paramValues.put("in", in.toString().toLowerCase(Locale.ENGLISH));
                        }
                        JsonNode jsonNode = jsonMapper.valueToTree(paramValues);
                        try {
                            Parameter value = jsonMapper.treeToValue(jsonNode, Parameter.class);
                            return Optional.ofNullable(value);
                        } catch (JsonProcessingException e) {
                            context.warn("Error reading Swagger Parameter for element [" + parameter + "]: " + e.getMessage(), parameter);
                            return Optional.empty();
                        }
                    });

                    if (opt.isPresent()) {
                        newParameter = opt.get();
                    }
                } else if (!parameter.hasStereotype(Bindable.class) && pathVariables.contains(parameterName)) {
                    newParameter = new Parameter();
                    newParameter.setIn(ParameterIn.PATH.toString());
                    newParameter.setExplode(matchTemplate.isExploded(parameterName));
                } else if (parameter.isAnnotationPresent(Header.class)) {
                    String headerName = parameter.getValue(Header.class, "name", String.class).orElseGet(() -> NameUtils.hyphenate(parameterName));
                    newParameter = new Parameter();
                    newParameter.setIn(ParameterIn.HEADER.toString());
                    newParameter.setName(headerName);
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
                        schema = resolveSchema(parameterType, consumesMediaType);
                    }

                    if (schema != null) {
                        newParameter.setSchema(schema);
                    }
                }

            }

        });
    }

    private Content buildContent(ClassElement type, String mediaType) {
        Content content = new Content();
        io.swagger.v3.oas.models.media.MediaType mt = new io.swagger.v3.oas.models.media.MediaType();
        mt.setSchema(resolveSchema(type, mediaType));
        content.addMediaType(mediaType, mt);
        return content;
    }

    private Schema resolveSchema(ClassElement type, String mediaType) {
        Schema schema = null;

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
                String componentType = type.getFirstTypeArgument().map(Element::getName).orElse(Object.class.getName());
                schema = getPrimitiveType(componentType);
                schema = arraySchema(schema);
            }

        }

        if (schema != null) {
            boolean isStream = MediaType.TEXT_EVENT_STREAM.equals(mediaType) || MediaType.APPLICATION_JSON_STREAM.equals(mediaType);
            if ((!isStream && isPublisher) || type.isIterable()) {
                schema = arraySchema(schema);
            }
        }
        return schema;
    }

    private ArraySchema arraySchema(Schema schema) {
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
