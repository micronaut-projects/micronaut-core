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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a top-level Java annotation into the experimental typed member-map generator.
 * Generates a sibling {@code MyAnnotation$AnnotationMap} interface extending
 * {@code Map<CharSequence, Object>}, a live adapter and immutable cached storage.
 * This prototype supports only {@code String} and {@code int} members with defaults.
 * Members conflicting with Map method names use a {@code member_} prefix.
 *
 * <p>The generated {@code of(Map)} factory preserves an existing typed instance or
 * creates a live adapter. {@code immutable(Map)} creates a snapshot. Missing keys
 * remain absent from the map even when typed accessors return annotation defaults.</p>
 *
 * @since 5.2.3
 */
@Experimental
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface GenerateAnnotationMap {
}
