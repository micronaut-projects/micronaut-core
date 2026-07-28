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
package io.micronaut.python.compiler;

import java.util.List;

/**
 * Generic CRUD repository used by Python bridge signature tests.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 */
public interface GenericCrudRepository<E, ID> {

    /**
     * Save an entity.
     *
     * @param entity The entity
     * @return The saved entity
     * @param <S> The concrete entity subtype
     */
    <S extends E> S save(S entity);

    /**
     * Save all entities.
     *
     * @param entities The entities
     * @return The saved entities
     * @param <S> The concrete entity subtype
     */
    <S extends E> List<S> saveAll(Iterable<S> entities);

    /**
     * Update all entities.
     *
     * @param entities The entities
     * @return The updated entities
     * @param <S> The concrete entity subtype
     */
    <S extends E> List<S> updateAll(Iterable<S> entities);

    /**
     * Delete all entities.
     *
     * @param entities The entities
     */
    void deleteAll(Iterable<? extends E> entities);
}
