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

package io.micronaut.core.reflect;

/**
 * An interface that can be implemented by classes that wish to listen to the classloading requirements for the an application. The {@link #close()} method will be called when the application terminates.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ClassLoadingReporter extends AutoCloseable {

    /**
     * Called when a class is present.
     *
     * @param type The type
     */
    void reportPresent(Class<?> type);

    /**
     * Called when a class is missing.
     *
     * @param name The name of the class
     */
    void reportMissing(String name);

    @Override
    void close();
}
