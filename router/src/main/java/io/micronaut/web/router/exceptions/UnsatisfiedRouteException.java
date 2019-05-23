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
     * @param argument The {@link Argument}
     */
    public UnsatisfiedRouteException(Argument<?> argument) {
        super(buildMessage(argument));
        this.argument = argument;
    }

    /**
     * @return The argument
     */
    public Argument<?> getArgument() {
        return argument;
    }

    private static String buildMessage(Argument<?> argument) {

        Optional<Class<? extends Annotation>> classOptional = argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Bindable.class);

        if (classOptional.isPresent()) {
            Class<? extends Annotation> clazz = classOptional.get();
            Optional<Object> valOptional = argument.getAnnotationMetadata().getValue(clazz);
            if (valOptional.isPresent()) {
                return "Required " + clazz.getSimpleName() + " [" + valOptional.get().toString() + "] not specified";
            } else {
                return "Required " + clazz.getSimpleName() + " [" + argument + "] not specified";
            }
        }

        return "Required argument [" + argument + "] not specified";
    }
}
