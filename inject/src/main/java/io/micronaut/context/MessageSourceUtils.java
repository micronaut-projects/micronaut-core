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
package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class used by {@link MessageSource} to create variables maps.
 * @author Sergio del Amo
 * @since 3.4.x
 */
public class MessageSourceUtils {
    /**
     * Returns a Map whose keys are the index of the varargs.
     * E.g. for "Sergio", "John" the map ["0" => "Sergio", "1" => "John"] is returned
     * @param args variables
     * @return The variables map.
     */
    @NonNull
    public static Map<String, Object> variables(@NonNull Object... args) {
        Map<String, Object> variables = new HashMap<>();
        int count = 0;
        for (Object value : args) {
            variables.put("" + count, value);
            count++;
        }
        return variables;
    }
}
