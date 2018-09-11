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

import io.micronaut.context.Qualifier;

/**
 * Thrown when no such beans exists.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NoSuchBeanException extends BeanContextException {

    /**
     * @param beanType The bean type
     */
    public NoSuchBeanException(Class beanType) {
        super("No bean of type [" + beanType.getName() + "] exists. Ensure the class is declared a bean and if you are using Java or Kotlin make sure you have enabled annotation processing.");
    }

    /**
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The type
     */
    public <T> NoSuchBeanException(Class<T> beanType, Qualifier<T> qualifier) {
        super("No bean of type [" + beanType.getName() + "] exists" + (qualifier != null ? " for the given qualifier: " + qualifier : "") + ". Ensure the class is declared a bean and if you are using Java or Kotlin make sure you have enabled annotation processing.");
    }

    /**
     * @param message The message
     */
    protected NoSuchBeanException(String message) {
        super(message);
    }
}
