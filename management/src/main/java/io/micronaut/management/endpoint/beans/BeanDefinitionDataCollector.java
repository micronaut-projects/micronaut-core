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
package io.micronaut.management.endpoint.beans;

/**
 * <p>Used to respond with bean information used for the {@link BeansEndpoint}.</p>
 *
 * @param <T> The type
 * @author James Kleeh
 * @since 1.0
 */
public interface BeanDefinitionDataCollector<T> {

    /**
     * @return The raw data representing information about the available beans;
     * the given bean definitions
     */
    T getData();
}
