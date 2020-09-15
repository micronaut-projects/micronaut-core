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
import io.micronaut.inject.BeanType;

/**
 * Thrown when no such beans exists.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanInstantiationException extends BeanCreationException {

    /**
     * @param message The message
     * @param cause   The throwable
     */
    public BeanInstantiationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message The message
     */
    public BeanInstantiationException(String message) {
        super(message);
    }

    /**
     * @param resolutionContext The resolution context
     * @param cause             The throwable
     */
    public BeanInstantiationException(BeanResolutionContext resolutionContext, Throwable cause) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, cause.getMessage()), cause);
    }

    /**
     * @param resolutionContext The resolution context
     * @param message           The message
     */
    public BeanInstantiationException(BeanResolutionContext resolutionContext, String message) {
        super(resolutionContext, MessageUtils.buildMessage(resolutionContext, message));
    }

    /**
     * @param beanDefinition The bean definition
     * @param cause          The throwable
     * @param <T>            The bean type
     */
    public <T> BeanInstantiationException(BeanType<T> beanDefinition, Throwable cause) {
        super(beanDefinition, "Error instantiating bean of type [" + beanDefinition.getName() + "]: " + cause.getMessage(), cause);
    }

    /**
     * @param beanDefinition The bean definition
     * @param message        The message
     * @param <T>            The bean type
     */
    public <T> BeanInstantiationException(BeanType<T> beanDefinition, String message) {
        super(beanDefinition, "Error instantiating bean of type [" + beanDefinition.getName() + "]: " + message);
    }
}
