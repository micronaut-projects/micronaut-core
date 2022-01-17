/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tracing.instrument.http;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @since 3.3.0
 */
@ConfigurationProperties(TracingExclusionsConfiguration.PREFIX)
public class TracingExclusionsConfiguration {

    public static final String PREFIX = "tracing";
    private List<String> exclusions;

    /**
     * @return The URI patterns to exclude from the tracing.
     */
    @Nullable public List<String> getExclusions() {
        return exclusions;
    }

    /**
     * Sets the URI patterns to be excluded from tracing.
     *
     * @param exclusions A list of regular expression patterns to be excluded from tracing if the request URI matches.
     *
     * @see java.util.regex.Pattern#compile(String)
     */
    public void setExclusions(@Nullable List<String> exclusions) {
        this.exclusions = exclusions;
    }

    /**
     * @return Either null (implying everything should be included), or a Predicate which when given a URL path returns
     *         whether that path should be included in tracing.
     */
    @Nullable public Predicate<String> inclusionTest() {
        if (CollectionUtils.isEmpty(exclusions)) {
            return null;
        } else {
            List<Pattern> patterns = exclusions.stream().map(Pattern::compile).collect(Collectors.toList());
            return uri -> patterns.stream().noneMatch(pattern -> pattern.matcher(uri).matches());
        }
    }

    /**
     * @return Either null (implying everything should be included), or a Predicate which when given a URL path returns
     *         whether that path should be excluded from tracing.
     */
    @Nullable public Predicate<String> exclusionTest() {
        Predicate<String> inclusionTest = inclusionTest();
        return inclusionTest == null ? null : inclusionTest.negate();
    }
}
