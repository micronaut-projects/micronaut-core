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
package io.micronaut.http.cookie;

import io.micronaut.http.exceptions.HttpException;

/**
 * Exception thrown when attempting to set a cookie with a size higher than the cookie size limit.
 *
 * @author Sergio del Amo
 * @since 4.9.1
 */
public final class CookieSizeExceededException extends HttpException {
    private final String cookieName;
    private final int maxSize;
    private final int size;

    /**
     * @param cookieName Cookie Name
     * @param maxSize Max Cookie Size Bytes
     * @param size Cookie Size Bytes
     */
    public CookieSizeExceededException(String cookieName, int maxSize, int size) {
        super("The cookie [%s] byte size [%d] exceeds the maximum cookie size [%d]"
            .formatted(cookieName, size, maxSize));
        this.cookieName = cookieName;
        this.maxSize = maxSize;
        this.size = size;
    }

    /**
     * Cookie Name.
     * @return Cookie Name
     */
    public String getCookieName() {
        return cookieName;
    }

    /**
     * Max Cookie Size Bytes.
     * @return Max Cookie Size Bytes
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Cookie Size Bytes.
     * @return Cookie Size Bytes
     */
    public int getSize() {
        return size;
    }
}
