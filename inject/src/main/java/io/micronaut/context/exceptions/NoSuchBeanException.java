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

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

/**
 * Thrown when no such beans exists.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NoSuchBeanException extends BeanContextException {

    private static final String MESSAGE_PREFIX = "No bean of type [";
    private static final String MESSAGE_SUFFIX = "] exists.";
    private static final String MESSAGE_EXISTS = "] exists";
    private static final String MESSAGE_FOR_THE_GIVEN_QUALIFIER = " for the given qualifier: ";

    /**
     * @param beanType The bean type
     */
    public NoSuchBeanException(@NonNull Class<?> beanType) {
        super(MESSAGE_PREFIX + beanType.getName() + MESSAGE_SUFFIX + additionalMessage());
    }

    /**
     * @param beanType The bean type
     */
    public NoSuchBeanException(@NonNull Argument<?> beanType) {
        super(MESSAGE_PREFIX + beanType.getTypeName() + MESSAGE_SUFFIX + additionalMessage());
    }

    /**
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The type
     */
    public <T> NoSuchBeanException(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        super(MESSAGE_PREFIX + beanType.getName() + MESSAGE_EXISTS + (qualifier != null ? MESSAGE_FOR_THE_GIVEN_QUALIFIER + qualifier : "") + "." + additionalMessage());
    }

    /**
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The type
     */
    public <T> NoSuchBeanException(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        super(MESSAGE_PREFIX + beanType.getTypeName() + MESSAGE_EXISTS + (qualifier != null ? MESSAGE_FOR_THE_GIVEN_QUALIFIER + qualifier : "") + "." + additionalMessage());
    }

    /**
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param message   The message
     * @param <T>       The type
     * @since 4.0.0
     */
    public <T> NoSuchBeanException(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier, String message) {
        super(MESSAGE_PREFIX + beanType.getTypeName() + MESSAGE_EXISTS + (qualifier != null ? MESSAGE_FOR_THE_GIVEN_QUALIFIER + qualifier : "") + ". " + message);
    }

    /**
     * @param message The message
     */
    protected NoSuchBeanException(String message) {
        super(message);
    }

    @NonNull
    private static String additionalMessage() {
        return " Make sure the bean is not disabled by bean requirements (enable trace logging for 'io.micronaut.context.condition' to check) and if the bean is enabled then ensure the class is declared a bean and annotation processing is enabled (for Java and Kotlin the 'micronaut-inject-java' dependency should be configured as an annotation processor).";
    }
}
