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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Removes the hop-by-hop headers from Netty headers, for the raw and proxy exchanges of the Netty
 * client, see {@link io.micronaut.http.client.RawRequestOptions#isStripHopByHopHeaders()}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class HopByHopHeaders {
    private static final AsciiString PROXY_PREFIX = AsciiString.cached("proxy-");

    private HopByHopHeaders() {
    }

    /**
     * Remove {@code Connection} and the headers it lists, {@code Keep-Alive}, {@code Proxy-*},
     * {@code TE}, {@code Trailer}, {@code Transfer-Encoding} and {@code Upgrade}.
     *
     * @param headers The headers to change
     */
    static void strip(HttpHeaders headers) {
        if (headers.isEmpty()) {
            return;
        }
        for (String connection : headers.getAll(HttpHeaderNames.CONNECTION)) {
            int length = connection.length();
            int start = 0;
            while (start < length) {
                int comma = connection.indexOf(',', start);
                int end = comma < 0 ? length : comma;
                String name = connection.substring(start, end).trim();
                if (!name.isEmpty()) {
                    headers.remove(name);
                }
                start = end + 1;
            }
        }
        headers.remove(HttpHeaderNames.CONNECTION)
            .remove(HttpHeaderNames.KEEP_ALIVE)
            .remove(HttpHeaderNames.TE)
            .remove(HttpHeaderNames.TRAILER)
            .remove(HttpHeaderNames.TRANSFER_ENCODING)
            .remove(HttpHeaderNames.UPGRADE);
        List<CharSequence> proxyHeaders = null;
        Iterator<Map.Entry<CharSequence, CharSequence>> iterator = headers.iteratorCharSequence();
        while (iterator.hasNext()) {
            CharSequence name = iterator.next().getKey();
            if (AsciiString.regionMatches(name, true, 0, PROXY_PREFIX, 0, PROXY_PREFIX.length())) {
                if (proxyHeaders == null) {
                    proxyHeaders = new ArrayList<>(2);
                }
                proxyHeaders.add(name);
            }
        }
        if (proxyHeaders != null) {
            for (CharSequence name : proxyHeaders) {
                headers.remove(name);
            }
        }
    }
}
