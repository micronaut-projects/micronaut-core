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
package io.micronaut.inject;

import io.micronaut.core.annotation.AnnotationMetadataProvider;

/**
 * Defines an injection point for a method.
 *
 * @param <B> The bean type
 * @param <T> The injectable type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInjectionPoint<B, T> extends CallableInjectionPoint<B>, AnnotationMetadataProvider {

    /**
     * @return The method name
     */
    String getName();

    /**
     * @return Is this method a pre-destroy method
     */
    boolean isPreDestroyMethod();

    /**
     * @return Is this method a post construct method
     */
    boolean isPostConstructMethod();

    default Class<B> getDeclaringType() {
        return getDeclaringBean().getBeanType();
    }
}
