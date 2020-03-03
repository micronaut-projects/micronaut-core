/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.exceptions;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;

/**
 * Represents a circular dependency failure.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class CircularDependencyException extends DependencyInjectionException {

    /**
     * @param resolutionContext The resolution context
     * @param argument          The argument
     * @param message           The message
     */
    public CircularDependencyException(BeanResolutionContext resolutionContext, Argument argument, String message) {
        super(resolutionContext, argument, message, true);
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param message             The message
     */
    public CircularDependencyException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message) {
        super(resolutionContext, fieldInjectionPoint, message, true);
    }

    /**
     * @param resolutionContext    The resolution context
     * @param methodInjectionPoint The method injection point
     * @param argument             The argument
     * @param message              The message
     */
    public CircularDependencyException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, String message) {
        super(resolutionContext, methodInjectionPoint, argument, message, true);
    }
}
