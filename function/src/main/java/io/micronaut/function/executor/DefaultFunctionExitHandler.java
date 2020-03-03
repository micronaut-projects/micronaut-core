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
 * Default implementation that will exit using {@link System#exit(int)}.
 *
 * @author graemerocher
 * @since 1.0
 */
public class DefaultFunctionExitHandler implements FunctionExitHandler {
    @Override
    public void exitWithError(Exception error, boolean debug) {
        FunctionApplication.exitWithError(debug, error);
    }

    @Override
    public void exitWithSuccess() {
        System.exit(0);
    }

    @Override
    public void exitWithNoData() {
        FunctionApplication.exitWithNoData();
    }
}
