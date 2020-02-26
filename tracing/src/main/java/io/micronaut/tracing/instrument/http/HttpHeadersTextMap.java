/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.instrument.http;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpHeaders;
import io.opentracing.propagation.TextMap;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A {@link TextMap} implementations for the headers.
 *
 * @author graemerocher
 * @since 1.0
 */
class HttpHeadersTextMap implements TextMap {
    private final HttpHeaders headers;

    /**
     * Initialize headers.
     *
     * @param headers The HTTP headers
     */
    HttpHeadersTextMap(HttpHeaders headers) {
        this.headers = headers;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        Iterator<Map.Entry<String, List<String>>> i = headers.iterator();
        return new Iterator<Map.Entry<String, String>>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Map.Entry<String, String> next() {
                Map.Entry<String, List<String>> entry = i.next();
                return new Map.Entry<String, String>() {

                    @Override
                    public String getKey() {
                        return entry.getKey();
                    }

                    @Override
                    public String getValue() {
                        List<String> value = entry.getValue();
                        if (CollectionUtils.isNotEmpty(value)) {
                            return value.get(0);
                        }
                        return null;
                    }

                    @Override
                    public String setValue(String value) {
                        String v = getValue();
                        entry.setValue(Collections.singletonList(value));
                        return v;
                    }
                };
            }
        };
    }

    @Override
    public void put(String key, String value) {
        if (headers instanceof MutableHttpHeaders) {
            ((MutableHttpHeaders) headers).add(key, value);
        }
    }
}
