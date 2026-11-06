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
package io.micronaut.python.annotation.processing.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java collections of Longs, whose elements a compiled body does not box a Python int as. */
public final class LongCollections {

    private LongCollections() {
    }

    /**
     * @return A list of Longs
     */
    public static List<Long> ids() {
        return new ArrayList<>(List.of(1L, 2L, 3L));
    }

    /**
     * @return A map keyed by Longs
     */
    public static Map<Long, String> names() {
        return new LinkedHashMap<>(Map.of(1L, "one"));
    }
}
