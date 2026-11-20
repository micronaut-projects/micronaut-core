/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev;

/**
 * How the application's main is run, on the thread the runtime provides.
 * @since 5.3.0
 */
@FunctionalInterface
interface ApplicationLauncher {
    /**
     * Runs the application. Returns when main returns, which for a server is when the context stops.
     *
     * @param classLoader The loader to load the main class from
     * @param mainClass The main class name
     * @param args The arguments
     * @throws Exception if main throws
     */
    void launch(ClassLoader classLoader, String mainClass, String[] args) throws Exception; // NOSONAR the application's main may throw any exception
}
