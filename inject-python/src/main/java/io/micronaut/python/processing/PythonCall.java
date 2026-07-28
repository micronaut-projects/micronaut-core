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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Experimental;

import java.util.List;
import java.util.Map;

/**
 * A call expression discovered in Python source without evaluating that source.
 *
 * @param name short call name
 * @param arguments literal positional arguments
 * @param keywordArguments literal keyword arguments by name
 */
@Experimental
public record PythonCall(String name, List<String> arguments, Map<String, String> keywordArguments) {
    public PythonCall {
        arguments = List.copyOf(arguments);
        keywordArguments = Map.copyOf(keywordArguments);
    }
}
