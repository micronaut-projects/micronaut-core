/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.retry.annotation;

import io.micronaut.core.annotation.Introspected;

import java.util.function.Predicate;

/**
 * An interface allows to provide custom condition for {@link io.micronaut.retry.annotation.Retryable} and
 * {@link io.micronaut.retry.annotation.CircuitBreaker}.
 *
 * @author Anton Tsukanov
 * @since 2.0
 */
@Introspected
@FunctionalInterface
public interface RetryPredicate extends Predicate<Throwable> {
}
