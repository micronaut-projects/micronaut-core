/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.validation.routes;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Visitor that checks validity of {@link ServerFilter} and {@link ClientFilter} classes.
 */
public final class FilterVisitor implements TypeElementVisitor<Object, Object> {
    private static final Set<Class<?>> PERMITTED_CLASSES = Set.of(
        void.class,
        HttpRequest.class,
        MutableHttpRequest.class,
        HttpResponse.class,
        MutableHttpResponse.class,
        FilterContinuation.class,
        Optional.class,
        Throwable.class,
        MutablePropagatedContext.class,
        RouteMatch.class,
        RouteInfo.class
    );
    private static final Set<String> PERMITTED_BINDING_ANNOTATIONS = Set.of(
        Body.class.getName(),
        Header.class.getName(),
        QueryValue.class.getName(),
        CookieValue.class.getName()
    );

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(
            ServerFilter.class.getName(),
            ClientFilter.class.getName(),
            RequestFilter.class.getName(),
            ResponseFilter.class.getName()
        );
    }

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyMethods();
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        AnnotationValue<RequestFilter> requestFilterAnnotation = element.getAnnotation(RequestFilter.class);
        AnnotationValue<ResponseFilter> responseFilterAnnotation = element.getAnnotation(ResponseFilter.class);
        if (requestFilterAnnotation == null && responseFilterAnnotation == null) {
            return;
        }

        if (!element.getDeclaringType().isAnnotationPresent(ServerFilter.class) &&
            !element.getDeclaringType().isAnnotationPresent(ClientFilter.class)) {

            context.fail("Filter method must be declared on a @ServerFilter or @ClientFilter bean", element);
            return;
        }

        try {
            ParameterElement[] parameters = element.getParameters();
            boolean isResponseFilter = responseFilterAnnotation != null;
            ParameterElement continuationCreator = null;
            for (ParameterElement parameter : parameters) {
                ClassElement parameterType = parameter.getGenericType();
                if (parameter.hasStereotype(Bindable.class)) {
                    String annotationName = parameter.getAnnotationNameByStereotype(Bindable.class).orElse(null);
                    if (!PERMITTED_BINDING_ANNOTATIONS.contains(annotationName)) {
                        context.fail("Unsupported binding annotation on filter method: " + annotationName, parameter);
                        return;
                    } else if (Body.class.getName().equals(annotationName) && isResponseFilter) {
                        context.fail("Cannot bind @Body for response filter method", parameter);
                        return;
                    } else if (Body.class.getName().equals(annotationName) && !isPermittedRawType(parameterType)) {
                        context.fail("The @Body to a filter method can only be a raw type (byte[], String, ByteBuffer etc.)", parameter);
                        return;
                    }
                    continue;
                }
                if (isInvalidType(context, parameter, parameterType, "Unsupported filter method parameter type")) {
                    return;
                }
                if (parameterType.isAssignable(HttpResponse.class)) {
                    if (!isResponseFilter) {
                        context.fail("Filter is called before the response is known, can't have a response argument", parameter);
                        return;
                    }
                } else if (parameterType.isAssignable(Throwable.class)) {
                    if (!isResponseFilter) {
                        context.fail("Request filters cannot handle exceptions", parameter);
                        return;
                    }
                } else if (parameterType.isAssignable(FilterContinuation.class)) {
                    if (isResponseFilter) {
                        context.fail("Response filters cannot use filter continuations", parameter);
                        return;
                    }

                    if (continuationCreator != null) {
                        context.fail("Only one continuation per filter is allowed", parameter);
                        return;
                    }
                    continuationCreator = parameter;
                    ClassElement continuationReturnType = resolveType(parameterType.getFirstTypeArgument().orElse(ClassElement.of(Object.class)));
                    if (!continuationReturnType.isAssignable(HttpResponse.class) && !continuationReturnType.isAssignable(MutableHttpResponse.class)) {
                        context.fail("Unsupported continuation type: " + continuationReturnType.getName(), parameter);
                        return;
                    }
                }
            }
            ClassElement returnType = resolveReturnType(element);
            if (!returnType.isVoid()) {
                if (isInvalidType(context, element, returnType, "Unsupported filter return type")) {
                    return;
                }
                if (isResponseFilter) {
                    if (!returnType.isAssignable(HttpResponse.class)) {
                        context.fail("Unsupported filter return type: " + returnType.getName(), element);
                    }
                } else {
                    if (continuationCreator != null && returnType.isAssignable(HttpRequest.class)) {
                        context.fail("Filter method that accepts a continuation cannot return an HttpRequest", element);
                        return;
                    }
                    if (!returnType.isAssignable(HttpRequest.class) && !returnType.isAssignable(HttpResponse.class)) {
                        context.fail("Unsupported filter return type: " + returnType.getName(), element);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            context.fail(e.getMessage(), element);
        }
    }

    private boolean isPermittedRawType(ClassElement parameterType) {
        if (parameterType.isArray() && parameterType.isPrimitive() && parameterType.getName().equals("byte")) {
            return true;
        }
        return parameterType.isAssignable(byte[].class) || parameterType.isAssignable(ByteBuffer.class) || parameterType.isAssignable(String.class);
    }

    private static ClassElement resolveReturnType(MethodElement element) {
        ClassElement returnType = element.getGenericReturnType();
        return resolveType(returnType);
    }

    private static ClassElement resolveType(ClassElement returnType) {
        if (returnType.isAssignable(Publisher.class) || returnType.isAssignable(CompletionStage.class) || returnType.isOptional()) {
            returnType = returnType.getFirstTypeArgument().orElse(returnType);
        }
        return returnType;
    }

    private static boolean isInvalidType(VisitorContext context, Element parameter, ClassElement parameterType, String message) {
        if (parameterType.isAssignable(Publisher.class) || parameterType.isAssignable(CompletionStage.class)) {
            parameterType = parameterType.getFirstTypeArgument().orElse(parameterType);
        }
        boolean valid = PERMITTED_CLASSES.stream().anyMatch(parameterType::isAssignable);
        if (!valid) {
            context.fail(message + ": " + parameterType.getName(), parameter);
            return true;
        }
        return false;
    }

}
