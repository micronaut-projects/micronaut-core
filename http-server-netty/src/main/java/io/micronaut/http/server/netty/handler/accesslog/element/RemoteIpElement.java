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
package io.micronaut.http.server.netty.handler.accesslog.element;

import java.util.Locale;
import java.util.Set;

import io.micronaut.http.HttpHeaders;
import io.netty.channel.socket.SocketChannel;

/**
 * RemoteIpElement LogElement. The remote IP address.
 *
 * @author croudet
 * @since 2.0
 */
final class RemoteIpElement implements LogElement {
    /**
     * The remote ip marker.
     */
    public static final String REMOTE_IP = "a";

    /** The HTTP {@code X-Forwarded-For} header field name (superseded by {@code Forwarded}). */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * The RemoteIpElement instance.
     */
    static final RemoteIpElement INSTANCE = new RemoteIpElement();

    private RemoteIpElement() {

    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public Set<Event> events() {
        return Event.REQUEST_HEADERS_EVENTS;
    }

    @Override
    public String onRequestHeaders(SocketChannel channel, String method, io.netty.handler.codec.http.HttpHeaders headers, String uri, String protocol) {
        // maybe this request was proxied or load balanced.
        // try and get the real originating IP
        final String xforwardedFor = headers.get(X_FORWARDED_FOR, null);
        if (xforwardedFor == null) {
            final String forwarded = headers.get(HttpHeaders.FORWARDED, null);
            if (forwarded != null) {
                String inet = processForwarded(forwarded);
                if (inet != null) {
                    return inet;
                }
            }
        } else {
            return processXForwardedFor(xforwardedFor);
        }
        return channel.remoteAddress().getAddress().getHostAddress();
    }

    private static String processXForwardedFor(String xforwardedFor) {
        // can contain multiple IPs for proxy chains. the first ip is our
        // client.
        final int firstComma = xforwardedFor.indexOf(',');
        if (firstComma >= 0) {
            return xforwardedFor.substring(0, firstComma);
        } else {
            return xforwardedFor;
        }
    }

    private static String processForwarded(String forwarded) {
        final int firstComma = forwarded.indexOf(',');
        final String firstForward = (firstComma >= 0 ? forwarded.substring(0, firstComma) : forwarded)
                .toLowerCase(Locale.US);
        int startIndex = firstForward.indexOf("for");
        if (startIndex == -1) {
            return null;
        }
        final int semiColonIndex = firstForward.indexOf(';');
        final int endIndex = semiColonIndex >= 0 ? semiColonIndex : firstForward.length();
        // skip 'for='
        startIndex += 4;
        // consume space and '='
        while (startIndex < endIndex) {
            char c = firstForward.charAt(startIndex);
            if (Character.isWhitespace(c) || c == '=') {
                ++startIndex;
            } else {
                return firstForward.substring(startIndex, endIndex);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return '%' + REMOTE_IP;
    }
}
