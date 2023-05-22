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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.Collection;
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
        Optional.class
    );

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(
            RequestFilter.class.getName(),
            ResponseFilter.class.getName()
        );
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
            Argument<?>[] args = toArguments(Arrays.stream(element.getParameters()).map(ParameterElement::getType).toList(), context);
            Argument<?> ret = toArgument(element.getReturnType(), context);

            if (requestFilterAnnotation != null) {
                // will throw on validation error
                FilterRunner.validateFilterMethod(args, ret, false);
            }
            if (responseFilterAnnotation != null) {
                // will throw on validation error
                FilterRunner.validateFilterMethod(args, ret, true);
            }
        } catch (IllegalArgumentException e) {
            context.fail(e.getMessage(), element);
        }
    }

    private Argument<?>[] toArguments(Collection<ClassElement> classElements, VisitorContext context) {
        return classElements.stream().map(arg -> toArgument(arg, context)).toArray(Argument[]::new);
    }

    private Argument<?> toArgument(ClassElement classElement, VisitorContext context) {
        Class<?> cl = toClass(classElement, context);
        Argument<?>[] parameters;
        try {
            parameters = toArguments(classElement.getTypeArguments().values(), context);
        } catch (IllegalArgumentException e) {
            // we don't always need the type params, try the erased type
            return Argument.of(cl);
        }
        return Argument.of(cl, parameters);
    }

    private Class<?> toClass(ClassElement classElement, VisitorContext context) {
        for (Class<?> permittedClass : PERMITTED_CLASSES) {
            if (classElement.getName().equals(permittedClass.getName())) {
                return permittedClass;
            }
        }
        if (classElement.isAssignable(CompletionStage.class)) {
            return CompletionStage.class;
        }
        if (classElement.isAssignable(Throwable.class)) {
            return Throwable.class;
        }
        for (String reactiveTypeName : Publishers.getReactiveTypeNames()) {
            if (classElement.isAssignable(reactiveTypeName)) {
                return Publisher.class;
            }
        }
        throw new IllegalArgumentException("Unsupported type for filter method: " + classElement.getName());
    }
}
