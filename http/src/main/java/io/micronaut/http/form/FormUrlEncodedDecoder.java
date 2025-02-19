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
package io.micronaut.http.form;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.StringUtils;
import reactor.util.annotation.NonNull;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes a form url encoded string into a map.
 * @author Sergio del Amo
 * @since 4.7.1
 */
@Experimental
public interface FormUrlEncodedDecoder {
    /**
     *
     * @param formUrlEncodedString Form URL encoded String
     * @param charset Charset
     * @return a Map representation of the Form URL encoded String
     */
    @NonNull
    Map<String, Object> decode(@NonNull String formUrlEncodedString, @NonNull Charset charset);

    /**
     * Converts a map with value list of string to value Object. If the list of string has a single element, then the value is the element itself.
     * @param parameters Map of key string and value list of Strings
     * @return Map of key String and value Object
     */
    default Map<String, Object> flatten(Map<String, List<String>> parameters) {
        Map<String, Object> result = new LinkedHashMap<>(parameters.size());
        parameters.forEach((k, v) -> {
            if (v.size() > 1) {
                result.put(k, v);
            } else if (v.size() == 1 && StringUtils.isNotEmpty(v.get(0))) {
                result.put(k, v.get(0));
            } else {
                result.put(k, null);
            }
        });
        return result;
    }
}
