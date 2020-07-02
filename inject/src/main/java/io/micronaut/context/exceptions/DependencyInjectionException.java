/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.exceptions;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;

import java.util.Optional;

/**
 * Represents a runtime failure to perform dependency injection.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DependencyInjectionException extends BeanContextException {

    /**
     * @param resolutionContext The resolution context
     * @param argument          The argument
     * @param cause             The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, argument, !(cause instanceof BeanInstantiationException) ? cause.getMessage() : null, false), cause);
    }

    /**
     * @param resolutionContext The resolution context
     * @param argument          The argument
     * @param message           The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, String message) {
        super(MessageUtils.buildMessage(resolutionContext, argument, message, false));
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param cause               The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, fieldInjectionPoint, null, false), cause);
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param message             The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message) {
        super(MessageUtils.buildMessage(resolutionContext, fieldInjectionPoint, message, false));
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param message             The message
     * @param cause               The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, fieldInjectionPoint, message, false), cause);
    }

    /**
     * @param resolutionContext    The resolution context
     * @param methodInjectionPoint The method injection point
     * @param argument             The argument
     * @param cause                The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, methodInjectionPoint, argument, null, false), cause);
    }

    /**
     * @param resolutionContext    The resolution context
     * @param methodInjectionPoint The method injection point
     * @param argument             The argument
     * @param message              The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, String message) {
        super(MessageUtils.buildMessage(resolutionContext, methodInjectionPoint, argument, message, false));
    }

    /**
     * Builds an error message for attempted argument conversion on a argument.
     *
     * @param resolutionContext         The resolution context
     * @param argumentConversionContext The argument conversion context
     * @param property                  The property being resolved
     */

    public DependencyInjectionException(BeanResolutionContext resolutionContext, ArgumentConversionContext argumentConversionContext, String property) {
        super(MessageUtils.buildMessage(resolutionContext, argumentConversionContext.getArgument(), buildConversionMessage(property, argumentConversionContext), false));
    }

    /**
     * Builds an error message for attempted argument conversion on a method.
     *
     * @param resolutionContext    The resolution context
     * @param methodInjectionPoint The method injection point
     * @param conversionContext    The conversion context
     * @param property             The property being resolved
     */
    public DependencyInjectionException(
        BeanResolutionContext resolutionContext,
        MethodInjectionPoint methodInjectionPoint,
        ArgumentConversionContext conversionContext,
        String property) {
        super(MessageUtils.buildMessage(resolutionContext, methodInjectionPoint, conversionContext.getArgument(), buildConversionMessage(property, conversionContext), false));
    }

    /**
     * @param resolutionContext    The resolution context
     * @param methodInjectionPoint The method injection point
     * @param argument             The argument
     * @param message              The message
     * @param circular             Is the path circular
     */
    protected DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, String message, boolean circular) {
        super(MessageUtils.buildMessage(resolutionContext, methodInjectionPoint, argument, message, circular));
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param message             The message
     * @param circular            Is the path circular
     */
    protected DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message, boolean circular) {
        super(MessageUtils.buildMessage(resolutionContext, fieldInjectionPoint, message, circular));
    }

    /**
     * @param resolutionContext The resolution context
     * @param argument          The argument
     * @param message           The message
     * @param circular          Is the path circular
     */
    protected DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, String message, boolean circular) {
        super(MessageUtils.buildMessage(resolutionContext, argument, message, circular));
    }

    private static String buildConversionMessage(String property, ArgumentConversionContext conversionContext) {
        Optional<ConversionError> lastError = conversionContext.getLastError();
        if (lastError.isPresent()) {
            ConversionError conversionError = lastError.get();
            return "Error resolving property value [" + property + "]. Unable to convert value " + conversionError.getOriginalValue().map(o -> "[" + o + "]").orElse("") + " to target type [" + conversionContext.getArgument().getTypeString(true) + "] due to: " + conversionError.getCause().getMessage();
        } else {
            return "Error resolving property value [" + property + "]. Property doesn't exist";
        }
    }
}
