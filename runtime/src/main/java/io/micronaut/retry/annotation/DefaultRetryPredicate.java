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

import java.util.Collections;
import java.util.List;

/**
 * Default retry predicate.
 *
 * @author Anton Tsukanov
 * @since 2.0
 */
@Introspected
public class DefaultRetryPredicate implements RetryPredicate {

    private final List<Class<? extends Throwable>> includes;
    private final List<Class<? extends Throwable>> excludes;
    private final boolean hasIncludes;
    private final boolean hasExcludes;

    /**
     * @param includes Classes to include for retry
     * @param excludes Classes to exclude for retry
     */
    public DefaultRetryPredicate(List<Class<? extends Throwable>> includes, List<Class<? extends Throwable>> excludes) {
        this.includes = includes;
        this.excludes = excludes;
        this.hasIncludes = !includes.isEmpty();
        this.hasExcludes = !excludes.isEmpty();
    }

    /**
     * Default constructor.
     */
    public DefaultRetryPredicate() {
        this(Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public boolean test(Throwable exception) {
        if (hasIncludes && includes.stream().noneMatch(cls -> cls.isInstance(exception))) {
            return false;
        } else {
            return !(hasExcludes && excludes.stream().anyMatch(cls -> cls.isInstance(exception)));
        }
    }
}
