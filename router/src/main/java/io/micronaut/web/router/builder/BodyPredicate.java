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
package io.micronaut.web.router.builder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The condition of a body condition of a synchronous predicate, see
 * {@link RequestCondition#body(io.micronaut.core.type.Argument, Predicate)}: a completed stage,
 * and the predicate that the server tests without one, see {@link RequestCondition.Body#predicate()}.
 *
 * @param <T> The type of the body
 */
final class BodyPredicate<T> implements Function<T, CompletionStage<Boolean>> {

    private static final CompletionStage<Boolean> MET = CompletableFuture.completedStage(true);
    private static final CompletionStage<Boolean> UNMET = CompletableFuture.completedStage(false);

    private final Predicate<? super T> predicate;

    BodyPredicate(Predicate<? super T> predicate) {
        this.predicate = predicate;
    }

    /**
     * @return The predicate
     */
    Predicate<? super T> predicate() {
        return predicate;
    }

    @Override
    public CompletionStage<Boolean> apply(T value) {
        return predicate.test(value) ? MET : UNMET;
    }
}
