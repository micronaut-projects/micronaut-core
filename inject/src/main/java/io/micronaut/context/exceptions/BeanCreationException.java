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

import java.util.Optional;

/**
 * A base class for exceptions that occurred during bean creation.
 *
 * @author James Kleeh
 * @since 2.0.2
 */
public abstract class BeanCreationException extends BeanContextException {

    private final BeanType rootBeanType;

    /**
     * @param message The message
     * @param cause   The throwable
     */
    protected BeanCreationException(String message, Throwable cause) {
        super(message, cause);
        rootBeanType = null;
    }

    /**
     * @param message The message
     */
    protected BeanCreationException(String message) {
        super(message);
        rootBeanType = null;
    }

    /**
     * @param resolutionContext The resolution context
     * @param message           The message
     */
    protected BeanCreationException(BeanResolutionContext resolutionContext, String message) {
        super(message);
        rootBeanType = resolveRootBeanDefinition(resolutionContext);
    }

    /**
     * @param resolutionContext The resolution context
     * @param message           The message
     * @param cause             The throwable
     */
    protected BeanCreationException(BeanResolutionContext resolutionContext, String message, Throwable cause) {
        super(message, cause);
        rootBeanType = resolveRootBeanDefinition(resolutionContext);
    }

    /**
     * @param beanDefinition The bean definition
     * @param message        The message
     * @param cause          The throwable
     * @param <T>            The bean type
     */
    protected <T> BeanCreationException(BeanType<T> beanDefinition, String message, Throwable cause) {
        super(message, cause);
        rootBeanType = beanDefinition;
    }

    /**
     * @param beanDefinition The bean definition
     * @param message        The message
     * @param <T>            The bean type
     */
    protected <T> BeanCreationException(BeanType<T> beanDefinition, String message) {
        super(message);
        rootBeanType = beanDefinition;
    }

    private BeanType resolveRootBeanDefinition(BeanResolutionContext resolutionContext) {
        BeanType rootBeanType = null;
        if (resolutionContext != null) {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            if (!path.isEmpty()) {
                BeanResolutionContext.Segment segment = path.peek();
                rootBeanType = segment.getDeclaringType();
            } else {
                rootBeanType = resolutionContext.getRootDefinition();
            }
        }
        return rootBeanType;
    }

    /**
     * @return The reference to the root bean.
     */
    public Optional<BeanType> getRootBeanType() {
        return Optional.ofNullable(rootBeanType);
    }
}
