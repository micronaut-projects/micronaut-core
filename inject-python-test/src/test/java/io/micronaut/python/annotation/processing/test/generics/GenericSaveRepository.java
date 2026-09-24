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

import java.util.List;

/**
 * An interface whose methods declare a type variable bounded by the type variable of the interface,
 * as the {@code save} and {@code saveAll} methods of the Micronaut Data repositories do
 * ({@code <S extends E> Publisher<S> saveAll(Iterable<S>)}).
 *
 * @param <E> The entity type
 */
public interface GenericSaveRepository<E> {

    <S extends E> List<S> saveAll(Iterable<S> entities);

    <S extends E> S save(S entity);
}
