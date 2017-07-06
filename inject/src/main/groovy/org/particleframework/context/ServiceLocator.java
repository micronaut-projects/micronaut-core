/*
 * Copyright 2017 original authors
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
package org.particleframework.context;

import java.util.function.Predicate;

/**
 * <p>A ServiceLocator allows locating services registered via {@code META-INF/services} and forms an interface abstraction over {@link java.util.ServiceLoader}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ServiceLocator {

    /**
     * Find all the services for the given type
     *
     * @param type The type
     * @param <T> The generic type
     * @return The services
     */
    default <T> Iterable<T> findServices(Class<T> type) {
        return findServices(type, (String name)-> true);
    }


    /**
     * Find all services for the given type and condition
     *
     * @param type The type
     * @param condition A condition that accepts the name of the service and allows condition loading
     * @param <T> The generic type
     * @return The matching services
     */
    <T> Iterable<T> findServices(Class<T> type, Predicate<String> condition);
}
