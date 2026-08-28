/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MemberElement;

/**
 * Processor for Python script elements that can apply type-level default annotations
 * based on member element stereotypes.
 */
@Experimental
public interface PythonScriptElementProcessor {

    /**
     * Process a Python script element and its member element to apply default annotations.
     *
     * @param classElement the Python script class element
     * @param memberElement the member element being processed
     */
    void process(ClassElement classElement, MemberElement memberElement);
}
