/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.core.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * A mapping from environment variable names to Micronaut
 * property names. Those can be precomputed, or computed
 * on-the-fly when missing.
 *
 * @since 3.2.0
 */
@Internal
public final class EnvironmentProperties {
    private static final char[] DOT_DASH = new char[] {'.', '-'};
    private final EnvironmentProperties delegate;

    private final Map<String, List<String>> cache = new HashMap<>();

    private EnvironmentProperties(EnvironmentProperties delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a new environment to property names cache with the
     * supplied set of precomputed values.
     * @param preComputed a map from environment variable name to Micronaut property names
     * @return an environment properties cache
     */
    public static EnvironmentProperties of(@NonNull Map<String, List<String>> preComputed) {
        EnvironmentProperties current = new EnvironmentProperties(null);
        current.cache.putAll(preComputed);
        return current;
    }

    /**
     * Returns an immutable view of the environment variable names
     * to Micronaut property names cache.
     * @return the current state of the cache
     */
    @NonNull
    public Map<String, List<String>> asMap() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Creates a new environment properties cache which delegates
     * queries to the delegate, but will cache values into its
     * own cache when they are missing.
     * @param delegate the delegate
     * @return a new environment properties cache
     */
    public static EnvironmentProperties fork(EnvironmentProperties delegate) {
        return new EnvironmentProperties(delegate);
    }

    /**
     * Creates a new empty cache of environment variable names to Micronaut
     * properties cache.
     * @return an empty cache
     */
    public static EnvironmentProperties empty() {
        return new EnvironmentProperties(null);
    }

    /**
     * Returns, for an environment variable, the list of deduced Micronaut
     * property names. The list is cached, so any further requests are
     * guaranteed to return in constant time.
     * @param env the name of the environment variable
     * @return the list of deduced Micronaut property names
     */
    public List<String> findPropertyNamesForEnvironmentVariable(String env) {
        if (delegate != null) {
            List<String> result = delegate.cache.get(env);
            if (result != null) {
                return result;
            }
        }
        // Keep the anonymous class instead of Lambda to reduce the Lambda invocation overhead during the startup
        return cache.computeIfAbsent(env, new Function<String, List<String>>() {
            @Override
            public List<String> apply(String env1) {
                return computePropertiesFor(env1);
            }
        });
    }

    private static List<String> computePropertiesFor(String env) {
        env = env.toLowerCase(Locale.ENGLISH);

        char[] charArray = env.toCharArray();
        int[] separatorIndexes = new int[charArray.length];
        int separatorCount = 0;
        for (int i = 0; i < charArray.length; i++) {
            if (charArray[i] == '_') {
                separatorIndexes[separatorCount++] = i;
            }
        }

        if (separatorCount == 0) {
            return List.of(env);
        }
        //halves is used to determine when to flip the separator
        int[] halves = new int[separatorCount];
        //stores the separator per half
        byte[] separator = new byte[separatorCount];
        //the total number of permutations. 2 to the power of the number of separators
        int permutations = (int) Math.pow(2, separatorCount);

        //initialize the halves
        //ex 4, 2, 1 for A_B_C_D
        for (int i = 0; i < halves.length; i++) {
            int start = (i == 0) ? permutations : halves[i - 1];
            halves[i] = start / 2;
        }

        String[] properties = new String[permutations];

        for (int i = 0; i < permutations; i++) {
            int round = i + 1;
            for (int s = 0; s < separatorCount; s++) {
                //mutate the array with the separator
                charArray[separatorIndexes[s]] = DOT_DASH[separator[s]];
                if (round % halves[s] == 0) {
                    separator[s] ^= 1;
                }
            }
            properties[i] = new String(charArray);
        }

        return List.of(properties);
    }
}
