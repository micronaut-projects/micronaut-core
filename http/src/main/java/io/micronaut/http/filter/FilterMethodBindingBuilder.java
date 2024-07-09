/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.function.Predicate;

/**
 * Builder for filter method execution metadata.
 *
 * @since 4.6.0
 * @author Jonas Konrad
 */
@Internal
public final class FilterMethodBindingBuilder {
    // binders
    final FilterArgBinder[] fulfilled;
    AsyncFilterArgBinder[] asyncArgBinders = null;
    @Nullable
    Predicate<FilterMethodContext> filterCondition;
    boolean skipOnError;
    boolean filtersException = false;
    MethodFilter.ContinuationCreator continuationCreator = null;

    FilterMethodBindingBuilder(int argumentCount, boolean isResponseFilter) {
        skipOnError = isResponseFilter;
        fulfilled = new FilterArgBinder[argumentCount];
    }

    public void addFilterCondition(Predicate<FilterMethodContext> condition) {
        if (this.filterCondition == null) {
            this.filterCondition = condition;
        } else {
            this.filterCondition = this.filterCondition.and(condition);
        }
    }
}
