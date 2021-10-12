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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;

import java.util.Optional;

/**
 * Represents a runtime failure to perform dependency injection.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DependencyInjectionException extends BeanCreationException {

    /**
     * @param resolutionContext The resolution context
     * @param cause             The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, Throwable cause) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, !(cause instanceof BeanInstantiationException) ? cause.getMessage() : null, false), cause);
    }

    /**
     * @param resolutionContext The resolution context
     * @param argument          The argument
     * @param cause             The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, Throwable cause) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, argument, !(cause instanceof BeanInstantiationException) ? cause.getMessage() : null, false), cause);
    }

    /**
     * @param resolutionContext The resolution context
     * @param message           The message
     * @param cause             The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, String message, Throwable cause) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, message), cause);
    }

    /**
     * @param resolutionContext The resolution context
     * @param message           The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, String message) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, message, false));
    }

    /**
     * @param resolutionContext The resolution context
     * @param argument          The argument
     * @param message           The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, String message) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, argument, message, false));
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param cause               The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, Throwable cause) {
        this(resolutionContext, fieldInjectionPoint.getDeclaringBean(), fieldInjectionPoint.getName(), cause);
    }

    /**
     * @param resolutionContext   The resolution context
     * @param declaringBean       The declaring type
     * @param fieldName           The field name
     * @param cause               The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, BeanDefinition declaringBean, String fieldName, Throwable cause) {
        super(resolutionContext, MessageUtils.buildMessageForField(resolutionContext, declaringBean, fieldName, null, false), cause);
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param message             The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message) {
        this(resolutionContext, fieldInjectionPoint.getDeclaringBean(), fieldInjectionPoint.getName(), message);
    }

    /**
     * @param resolutionContext   The resolution context
     * @param declaringBean       The declaring bean
     * @param fieldName           The field name
     * @param message             The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, BeanDefinition declaringBean, String fieldName, String message) {
        super(resolutionContext, MessageUtils.buildMessageForField(resolutionContext, declaringBean, fieldName, message, false));
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param message             The message
     * @param cause               The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message, Throwable cause) {
        this(resolutionContext, fieldInjectionPoint.getDeclaringBean(), fieldInjectionPoint.getName(), message, cause);
    }

    /**
     * @param resolutionContext   The resolution context
     * @param declaringBean       The declaring bean
     * @param fieldName           The field name
     * @param message             The message
     * @param cause               The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, BeanDefinition declaringBean, String fieldName, String message, Throwable cause) {
        super(resolutionContext, MessageUtils.buildMessageForField(resolutionContext, declaringBean, fieldName, message, false), cause);
    }

    /**
     * @param resolutionContext    The resolution context
     * @param methodInjectionPoint The method injection point
     * @param argument             The argument
     * @param cause                The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, Throwable cause) {
        super(resolutionContext, MessageUtils.buildMessageForMethod(resolutionContext, methodInjectionPoint.getDeclaringBean(), methodInjectionPoint.getName(), argument, null, false), cause);
    }

    /**
     * @param resolutionContext    The resolution context
     * @param declaringType        The declaring type
     * @param methodName           The method name
     * @param argument             The argument
     * @param cause                The throwable
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, BeanDefinition declaringType, String methodName, Argument argument, Throwable cause) {
        super(resolutionContext, MessageUtils.buildMessageForMethod(resolutionContext, declaringType, methodName, argument, null, false), cause);
    }

    /**
     * @param resolutionContext    The resolution context
     * @param methodInjectionPoint The method injection point
     * @param argument             The argument
     * @param message              The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, String message) {
        this(resolutionContext, methodInjectionPoint.getDeclaringBean(), methodInjectionPoint.getName(), argument, message);
    }

    /**
     * @param resolutionContext    The resolution context
     * @param declaringType        The declaring type
     * @param methodName           The method name
     * @param argument             The argument
     * @param message              The message
     */
    public DependencyInjectionException(BeanResolutionContext resolutionContext, BeanDefinition declaringType, String methodName, Argument argument, String message) {
        super(resolutionContext, MessageUtils.buildMessageForMethod(resolutionContext, declaringType, methodName, argument, message, false));
    }

    /**
     * Builds an error message for attempted argument conversion on a argument.
     *
     * @param resolutionContext         The resolution context
     * @param argumentConversionContext The argument conversion context
     * @param property                  The property being resolved
     */

    public DependencyInjectionException(BeanResolutionContext resolutionContext, ArgumentConversionContext argumentConversionContext, String property) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, argumentConversionContext.getArgument(), buildConversionMessage(property, argumentConversionContext), false));
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
        this(resolutionContext, methodInjectionPoint.getDeclaringBean(), methodInjectionPoint.getName(), conversionContext, property);
    }

    /**
     * Builds an error message for attempted argument conversion on a method.
     *
     * @param resolutionContext    The resolution context
     * @param declaringBean        The declaring bean
     * @param methodName           The method name
     * @param conversionContext    The conversion context
     * @param property             The property being resolved
     */
    public DependencyInjectionException(
        BeanResolutionContext resolutionContext,
        BeanDefinition declaringBean,
        String methodName,
        ArgumentConversionContext conversionContext,
        String property) {
        super(resolutionContext, MessageUtils.buildMessageForMethod(resolutionContext, declaringBean, methodName, conversionContext.getArgument(), buildConversionMessage(property, conversionContext), false));
    }

    /**
     * @param resolutionContext    The resolution context
     * @param methodInjectionPoint The method injection point
     * @param argument             The argument
     * @param message              The message
     * @param circular             Is the path circular
     */
    protected DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, String message, boolean circular) {
        this(resolutionContext, methodInjectionPoint.getDeclaringBean(), methodInjectionPoint.getName(), argument, message, circular);
    }

    /**
     * @param resolutionContext    The resolution context
     * @param declaringType        The method declaring type
     * @param methodName           The method name
     * @param argument             The argument
     * @param message              The message
     * @param circular             Is the path circular
     */
    protected DependencyInjectionException(BeanResolutionContext resolutionContext, BeanDefinition declaringType, String methodName, Argument argument, String message, boolean circular) {
        super(resolutionContext, MessageUtils.buildMessageForMethod(resolutionContext, declaringType, methodName, argument, message, circular));
    }

    /**
     * @param resolutionContext   The resolution context
     * @param fieldInjectionPoint The field injection point
     * @param message             The message
     * @param circular            Is the path circular
     */
    protected DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message, boolean circular) {
        this(resolutionContext, fieldInjectionPoint.getDeclaringBean(), fieldInjectionPoint.getName(), message, circular);
    }

    /**
     * @param resolutionContext   The resolution context
     * @param declaringType       The field declaringType
     * @param fieldName       The field name
     * @param message             The message
     * @param circular            Is the path circular
     */
    protected DependencyInjectionException(BeanResolutionContext resolutionContext, BeanDefinition declaringType, String fieldName, String message, boolean circular) {
        super(resolutionContext, MessageUtils.buildMessageForField(resolutionContext, declaringType, fieldName, message, circular));
    }

    /**
     * @param resolutionContext The resolution context
     * @param argument          The argument
     * @param message           The message
     * @param circular          Is the path circular
     */
    protected DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, String message, boolean circular) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, argument, message, circular));
    }

    /**
     * Builds an error message for attempted argument conversion on a method.
     *
     * @param resolutionContext    The resolution context
     * @param conversionContext    The conversion context
     * @param property             The property being resolved
     * @return new instance of {@link DependencyInjectionException}
     */
    public static DependencyInjectionException missingProperty(BeanResolutionContext resolutionContext, ArgumentConversionContext conversionContext, String property) {
        return new DependencyInjectionException(resolutionContext, MessageUtils.buildMessage(resolutionContext, buildConversionMessage(property, conversionContext), false));
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
