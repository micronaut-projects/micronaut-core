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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MemberElement;

/**
 * Default processor that applies the Controller annotation when a method
 * with the HttpMethodMapping stereotype is found.
 */
public class ControllerScriptElementProcessor implements PythonScriptElementProcessor {

    @Override
    public void process(ClassElement classElement, MemberElement memberElement) {
        if (memberElement.hasStereotype("io.micronaut.http.annotation.HttpMethodMapping")) {
            classElement.annotate("io.micronaut.http.annotation.Controller");
        }
    }
}