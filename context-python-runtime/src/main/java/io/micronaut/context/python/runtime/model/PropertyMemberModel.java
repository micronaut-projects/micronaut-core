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
package io.micronaut.context.python.runtime.model;

/**
 * One declaration of a property: the accessor a type of the hierarchy declares, with its own annotation metadata.
 * Described only when the introspection separates the declarations.
 *
 * @param declaringTypeName The binary name of the type declaring the member
 * @param name              The member name
 * @param argument          The type of the member, carrying the member's own annotation metadata
 * @param readable          Whether the member is read through the accessor of the property, rather than not read at all
 * @since 5.3.0
 */
public record PropertyMemberModel(String declaringTypeName, String name, ArgumentModel argument, boolean readable) {
}
