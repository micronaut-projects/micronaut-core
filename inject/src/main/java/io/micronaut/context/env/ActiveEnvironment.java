/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.env;

/**
 * An environment that is active for the current execution
 * of the application.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
public interface ActiveEnvironment {

    /**
     * @return The name of the environment
     */
    String getName();

    /**
     * A 0 based index representing the priority
     * of the environment relative to other active
     * environments.
     *
     * @return The priority
     */
    int getPriority();

    /**
     * Creates a new active environment for the given arguments.
     *
     * @param name     The environment name
     * @param priority The priority
     * @return The active environment
     */
    static ActiveEnvironment of(String name, int priority) {
        return new ActiveEnvironment() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }
}
