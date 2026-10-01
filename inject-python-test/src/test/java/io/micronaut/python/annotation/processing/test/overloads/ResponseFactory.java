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
package io.micronaut.python.annotation.processing.test.overloads;

import java.util.Collection;
import java.util.Map;

/**
 * Overloads taking a collection or a map, as {@code AuthenticationResponse.success} does.
 */
public final class ResponseFactory {

    private ResponseFactory() {
    }

    public static String success(String username, Collection<String> roles) {
        return "collection:" + username + ":" + String.join(",", roles);
    }

    public static String success(String username, Map<String, Object> attributes) {
        return "map:" + username + ":" + attributes.size();
    }

    public static String describe(java.util.List<String> values) {
        return "list:" + values.size();
    }

    public static String describe(Map<String, Object> values) {
        return "map:" + values.size();
    }

    public static String first(Iterable<Object> values) {
        return "first:" + values.iterator().next();
    }
}
