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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Internal
public final class EnvironmentProperties {
    private static final char[] DOT_DASH = new char[] {'.', '-'};
    private final EnvironmentProperties delegate;

    private final Map<String, List<String>> cache = new HashMap<>();

    private EnvironmentProperties(EnvironmentProperties delegate) {
        this.delegate = delegate;
    }

    public static EnvironmentProperties of(Map<String, List<String>> preComputed) {
        EnvironmentProperties current = new EnvironmentProperties(null);
        current.cache.putAll(preComputed);
        return current;
    }

    public Map<String, List<String>> asMap() {
        return Collections.unmodifiableMap(cache);
    }

    public static EnvironmentProperties fork(EnvironmentProperties delegate) {
        return new EnvironmentProperties(delegate);
    }

    public static EnvironmentProperties empty() {
        return new EnvironmentProperties(null);
    }

    public List<String> findPropertyNamesForEnvironmentVariable(String env) {
        if (delegate != null) {
            List<String> result = delegate.cache.get(env);
            if (result != null) {
                return result;
            }
        }
        return cache.computeIfAbsent(env, EnvironmentProperties::computePropertiesFor);
    }

    private static List<String> computePropertiesFor(String env) {
        env = env.toLowerCase(Locale.ENGLISH);

        List<Integer> separatorIndexList = new ArrayList<>();
        char[] propertyArr = env.toCharArray();
        for (int i = 0; i < propertyArr.length; i++) {
            if (propertyArr[i] == '_') {
                separatorIndexList.add(i);
            }
        }

        if (!separatorIndexList.isEmpty()) {
            //store the index in the array where each separator is
            int[] separatorIndexes = separatorIndexList.stream().mapToInt(Integer::intValue).toArray();

            int separatorCount = separatorIndexes.length;
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
                    propertyArr[separatorIndexes[s]] = DOT_DASH[separator[s]];
                    if (round % halves[s] == 0) {
                        separator[s] ^= 1;
                    }
                }
                properties[i] = new String(propertyArr);
            }

            return Arrays.asList(properties);
        } else {
            return Collections.singletonList(env);
        }
    }
}
