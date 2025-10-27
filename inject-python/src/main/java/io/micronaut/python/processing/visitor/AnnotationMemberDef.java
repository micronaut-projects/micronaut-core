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

import java.util.List;

/**
 * Represents a member of a Python annotation (decorator parameter).
 * This record implements ElementDef to provide annotation member information
 * for Micronaut's annotation processing system.
 *
 * @param name The name of the annotation member
 * @author Micronaut Team
 * @since 5.0.0
 */
public record AnnotationMemberDef(String name) implements ElementDef {
    @Override
    public List<DecoratorDef> decorators() {
        return List.of();
    }
}
