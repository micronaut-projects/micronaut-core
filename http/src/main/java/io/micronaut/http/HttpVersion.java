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
package io.micronaut.http;

/**
 * Enum representing different HTTP versions.
 *
 * @author graemerocher
 * @since 2.0
 */
public enum HttpVersion {
    /**
     * {@code HTTP/1.0}.
     */
    HTTP_1_0,
    /**
     * {@code HTTP/1.1}.
     */
    HTTP_1_1,
    /**
     * {@code HTTP/2.0}.
     */
    HTTP_2_0;

    /**
     * Return an {@link HttpVersion} for the given value.
     * @param v The value
     * @return The version
     * @throws IllegalArgumentException If the given value is not a valid http version.
     */
    public static HttpVersion valueOf(double v) {
        if (v == 1.0d) {
            return HttpVersion.HTTP_1_0;
        } else if (v == 1.1d) {
            return HttpVersion.HTTP_1_1;
        } else if (v == 2.0) {
            return HttpVersion.HTTP_2_0;
        } else {
            throw new IllegalArgumentException("Invalid HTTP version: " + v);
        }
    }
}
