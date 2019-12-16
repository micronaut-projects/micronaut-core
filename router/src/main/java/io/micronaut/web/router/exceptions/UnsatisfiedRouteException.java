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
package io.micronaut.web.router.exceptions;

import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.annotation.*;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * An exception thrown when the an {@link Argument} to a {@link io.micronaut.web.router.Route} cannot be satisfied.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UnsatisfiedRouteException extends RoutingException {

    private final Argument<?> argument;

    /**
     * @param message  The error message
     * @param argument The {@link Argument}
     */
    UnsatisfiedRouteException(String message, Argument<?> argument) {
        super(message);
        this.argument = argument;
    }

    /**
     * Creates a specialized UnsatisfiedRouteException given the provided argument.
     *
     * @param argument The {@link Argument}
     * @return A UnsatisfiedRouteException
     */
    public static UnsatisfiedRouteException create(Argument<?> argument) {
        Optional<Class<? extends Annotation>> classOptional = argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Bindable.class);

        if (classOptional.isPresent()) {
            Class<? extends Annotation> clazz = classOptional.get();
            String name = argument.getAnnotationMetadata().stringValue(clazz).orElse(argument.getName());

            if (clazz == Body.class) {
                throw new UnsatisfiedBodyRouteException(name, argument);
            } else if (clazz == QueryValue.class) {
                throw new UnsatisfiedQueryValueRouteException(name, argument);
            } else if (clazz == PathVariable.class) {
                throw new UnsatisfiedPathVariableRouteException(name, argument);
            } else if (clazz == Header.class) {
                throw new UnsatisfiedHeaderRouteException(name, argument);
            } else if (clazz == Part.class) {
                throw new UnsatisfiedPartRouteException(name, argument);
            } else if (clazz == RequestAttribute.class) {
                throw new UnsatisfiedRequestAttributeRouteException(name, argument);
            } else if (clazz == CookieValue.class) {
                throw new UnsatisfiedCookieValueRouteException(name, argument);
            } else {
                throw new UnsatisfiedRouteException("Required " + clazz.getSimpleName() + " [" + name + "] not specified", argument);
            }
        }

        throw new UnsatisfiedRouteException("Required argument [" + argument.getName() + "] not specified", argument);
    }

    /**
     * @return The argument
     */
    public Argument<?> getArgument() {
        return argument;
    }
}
