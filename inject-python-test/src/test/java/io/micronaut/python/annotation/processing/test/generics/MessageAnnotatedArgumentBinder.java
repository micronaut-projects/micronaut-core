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
package io.micronaut.python.annotation.processing.test.generics;

import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;

import java.lang.annotation.Annotation;

/**
 * The {@code NatsAnnotatedArgumentBinder<A>} shape: the annotation type is a type variable while the
 * bound type is fixed by two super interfaces, each declaring {@code bind}.
 *
 * @param <A> The annotation type
 */
public interface MessageAnnotatedArgumentBinder<A extends Annotation>
    extends AnnotatedArgumentBinder<A, Object, String>, MessageArgumentBinder<Object> {
}
