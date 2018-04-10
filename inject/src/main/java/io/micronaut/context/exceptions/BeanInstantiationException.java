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
package io.micronaut.context.exceptions;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.inject.BeanType;

/**
 * Thrown when no such beans exists
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanInstantiationException extends BeanContextException {

    public BeanInstantiationException(String message, Throwable cause) {
        super(message, cause);
    }

    public BeanInstantiationException(String message) {
        super(message);
    }

    public BeanInstantiationException(BeanResolutionContext resolutionContext, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, cause.getMessage()), cause);
    }

    public BeanInstantiationException(BeanResolutionContext resolutionContext, String message) {
        super(MessageUtils.buildMessage(resolutionContext, message));
    }

    public <T> BeanInstantiationException(BeanType<T> beanDefinition, Throwable cause) {
        super("Error instantiating bean of type [" + beanDefinition.getName() + "]: " + cause.getMessage(), cause);
    }

    public <T> BeanInstantiationException(BeanType<T> beanDefinition, String message) {
        super("Error instantiating bean of type [" + beanDefinition.getName() + "]: " + message);
    }
}
