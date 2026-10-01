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
import java.util.function.Supplier;

/**
 * An interface whose methods declare their own type variables, as {@code JpaSpecificationExecutor}
 * ({@code <R> R findOne(CriteriaQueryBuilder<R>)}) and {@code SharedIndexInformerFactory} do.
 *
 * @param <T> The entity type
 */
public interface GenericQueryExecutor<T> {

    <R> R findOne(GenericQueryBuilder<R> builder);

    <R> List<R> findAll(GenericQueryBuilder<R> builder);

    <A extends T, L extends List<A>> Supplier<L> informerFor(Class<A> apiType, Class<L> apiListType, String namespace);

    int count(T entity);
}
