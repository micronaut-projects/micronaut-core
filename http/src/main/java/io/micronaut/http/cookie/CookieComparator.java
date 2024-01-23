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
package io.micronaut.http.cookie;

import java.util.Comparator;

/**
 * {@link Comparator} for {@link Cookie}.
 * Implementation mimics implementation of Netty {@link io.netty.handler.codec.http.cookie.DefaultCookie#compareTo(io.netty.handler.codec.http.cookie.Cookie)}.
 * @author Sergio del Amo
 * @since 4.3.0
 */
public final class CookieComparator implements Comparator<Cookie> {
    @Override
    public int compare(Cookie o1, Cookie o2) {
        int v = o1.getName().compareTo(o2.getName());
        if (v != 0) {
            return v;
        }

        if (o1.getPath() == null) {
            if (o2.getPath() != null) {
                return -1;
            }
        } else if (o2.getPath() == null) {
            return 1;
        } else {
            v = o1.getPath().compareTo(o2.getPath());
            if (v != 0) {
                return v;
            }
        }

        if (o1.getDomain() == null) {
            if (o2.getDomain() != null) {
                return -1;
            }
        } else if (o2.getDomain() == null) {
            return 1;
        } else {
            v = o1.getDomain().compareToIgnoreCase(o2.getDomain());
            return v;
        }

        return 0;
    }
}
