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

import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

import java.util.Collections;
import java.util.Iterator;


/**
 * Exception thrown when a bean is not unique and has multiple possible implementations for a given bean type.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("java:S110")
public class NonUniqueBeanException extends NoSuchBeanException {

    @SuppressWarnings("java:S3740")
    private final transient Iterator possibleCandidates;
    private final Class<?> targetType;

    /**
     * @param targetType The target type
     * @param candidates The bean definition candidates
     * @param <T>        The type
     */
    public <T> NonUniqueBeanException(Class<? extends T> targetType, Iterator<BeanDefinition<T>> candidates) {
        super(buildMessage(candidates));
        this.targetType = targetType;
        this.possibleCandidates = candidates;
    }

    /**
     * @param <T> The type
     * @return The possible bean candidates
     */
    public <T> Iterator<BeanDefinition<T>> getPossibleCandidates() {
        if (possibleCandidates != null) {
            return possibleCandidates;
        }
        return Collections.emptyIterator();
    }

    /**
     * @param <T> The type
     * @return The bean type requested
     */
    @SuppressWarnings("unchecked")
    public <T> Class<T> getBeanType() {
        return (Class<T>) targetType;
    }

    private static <T> String buildMessage(Iterator<BeanDefinition<T>> possibleCandidates) {
        final StringBuilder message = new StringBuilder("Multiple possible bean candidates found: [");
        while (possibleCandidates.hasNext()) {
            Argument<T> next = possibleCandidates.next().asArgument();
            message.append(next.getTypeString(true));
            if (possibleCandidates.hasNext()) {
                message.append(", ");
            }
        }
        message.append("]");
        return message.toString();
    }
}
