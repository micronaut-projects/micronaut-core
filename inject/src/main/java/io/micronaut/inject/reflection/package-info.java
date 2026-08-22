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
/**
 * Reflective implementations of the metadata the annotation processors generate: an
 * {@link io.micronaut.inject.ExecutableMethod} over a {@link java.lang.reflect.Method} and a
 * {@link io.micronaut.core.beans.BeanIntrospection} over a {@link java.lang.Class}, for the types that were
 * not processed at compilation time and that a specification requires to be handled anyway.
 *
 * @since 5.1
 */
package io.micronaut.inject.reflection;
