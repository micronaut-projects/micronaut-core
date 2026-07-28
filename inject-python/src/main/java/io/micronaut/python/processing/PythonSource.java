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

/**
 * Python source metadata made available during annotation processing.
 *
 * @param name source name
 * @param packageName source package name
 * @param calls calls discovered in the source
 */
@Experimental
public record PythonSource(String name, String packageName, List<PythonCall> calls) {
    public PythonSource {
        calls = List.copyOf(calls);
    }
}
