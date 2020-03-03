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
package io.micronaut.function.executor;

/**
 * A strategy interface for handling exiting from a function when it is executed via the CLI.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface FunctionExitHandler {
    /**
     * Exit the function with an error.
     *
     * @param error The error
     * @param debug Whether to output debug information before exiting
     */
    void exitWithError(Exception error, boolean debug);

    /**
     * Exit the function with success.
     */
    void exitWithSuccess();

    /**
     * Exit the function indicating no data was supplied.
     */
    void exitWithNoData();
}
