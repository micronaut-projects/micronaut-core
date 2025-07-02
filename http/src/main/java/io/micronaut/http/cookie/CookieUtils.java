/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import org.slf4j.Logger;

/**
 * Utils class to work with cookies.
 */
@Internal
public final class CookieUtils {

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-6.1">Cookie Limits</a>
     */
    private static final int COOKIE_BYTE_LIMIT = 4096;

    private CookieUtils() {
    }

    /**
     *
     * @param logger Logger
     * @param cookie Cookie
     * @param cookieEncoded Encoded cookie
     */
    public static void logCookieByteLimit(@NonNull Logger logger,
                                          @NonNull Cookie cookie,
                                          @NonNull String cookieEncoded) {
        if (logger.isWarnEnabled()) {
            int byteCount = StringUtils.byteCount(cookieEncoded);
            if (byteCount > COOKIE_BYTE_LIMIT) {
                logger.warn("Cookie {} size {} greater than limit {}", cookie.getName(), byteCount, COOKIE_BYTE_LIMIT);
            }
        }
    }
}
