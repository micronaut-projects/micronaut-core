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
package io.micronaut.http.server.netty.handler.accesslog.element;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;

/**
 * The access log format parser.
 *
 * The syntax is based on <a href="http://httpd.apache.org/docs/current/mod/mod_log_config.html">Apache httpd log format</a>.
 * Here are the supported directives:
 * <ul>
 * <li><b>%a</b> - Remote IP address</li>
 * <li><b>%A</b> - Local IP address</li>
 * <li><b>%b</b> - Bytes sent, excluding HTTP headers, or '-' if no bytes were sent</li>
 * <li><b>%B</b> - Bytes sent, excluding HTTP headers</li>
 * <li><b>%h</b> - Remote host name</li>
 * <li><b>%H</b> - Request protocol</li>
 * <li><b>%{<header>}i</b> - Request header. If the argument is omitted (<b>%i</b>) all headers will be printed</li>
 * <li><b>%{<header>}o</b> - Response header. If the argument is omitted (<b>%o</b>) all headers will be printed</li>
 * <li><b>%{<cookie>}C</b> - Request cookie (COOKIE). If the argument is omitted (<b>%C</b>) all cookies will be printed</li>
 * <li><b>%{<cookie>}c</b> - Response cookie (SET_COOKIE). If the argument is omitted (<b>%c</b>) all cookies will be printed</li>
 * <li><b>%l</b> - Remote logical username from identd (always returns '-')</li>
 * <li><b>%m</b> - Request method</li>
 * <li><b>%p</b> - Local port</li>
 * <li><b>%q</b> - Query string (excluding the '?' character)</li>
 * <li><b>%r</b> - First line of the request</li>
 * <li><b>%s</b> - HTTP status code of the response</li>
 * <li><b>%{<format>}t</b> - Date and time. If the argument is ommitted the Common Log Format format is used ("'['dd/MMM/yyyy:HH:mm:ss Z']'").
 * If the format starts with begin: (default) the time is taken at the beginning of the request processing. If it starts with end: it is the time when the log entry gets written, close to the end of the request processing.
 * The format should follow the DateTimeFormatter syntax.</li>
 * <li><b>%u</b> - Remote user that was authenticated. Not implemented. Prints '-'.</li>
 * <li><b>%U</b> - Requested URI</li>
 * <li><b>%v</b> - Local server name</li>
 * <li><b>%D</b> - Time taken to process the request, in millis</li>
 * <li><b>%T</b> - Time taken to process the request, in seconds</li>
 * </ul>
 * <p>In addition, the following aliases for commonly utilized patterns:</p>
 * <ul>
 * <li><b>common</b> - <code>%h %l %u %t "%r" %s %b</code> Common Log Format (CLF)</li>
 * <li><b>combined</b> -
 * <code>%h %l %u %t "%r" %s %b "%{Referer}i" "%{User-Agent}i"</code> Combined Log Format</li>
 * </ul>
 *
 * @author croudet
 * @since 2.0
 */
public class AccessLogFormatParser {
    /**
     * The combined log format.
     */
    public static final String COMBINED_LOG_FORMAT = "%h %l %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-Agent}i\"";
    /**
     * The common log format.
     */
    public static final String COMMON_LOG_FORMAT = "%h %l %u %t \"%r\" %s %b";

    private static final List<LogElementBuilder> LOG_ELEMENT_BUILDERS;

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessLogFormatParser.class);

    private final List<IndexedLogElement> onRequestElements = new ArrayList<>();
    private final List<IndexedLogElement> onResponseHeadersElements = new ArrayList<>();
    private final List<IndexedLogElement> onResponseWriteElements = new ArrayList<>();
    private final List<IndexedLogElement> onLastResponseWriteElements = new ArrayList<>();
    private final List<IndexedLogElement> constantElements = new ArrayList<>();
    private String[] elements;

    static {
        SoftServiceLoader<LogElementBuilder> builders = SoftServiceLoader.load(LogElementBuilder.class, LogElementBuilder.class.getClassLoader());
        LOG_ELEMENT_BUILDERS = new ArrayList<>();
        for (ServiceDefinition<LogElementBuilder> definition : builders) {
            if (definition.isPresent()) {
                LOG_ELEMENT_BUILDERS.add(definition.load());
            }
        }
        trimToSize(LOG_ELEMENT_BUILDERS);
    }

    /**
     * Creates an AccessLogFormatParser.
     *
     * @param spec The log format. When null the Common Log Format is used.
     */
    public AccessLogFormatParser(String spec) {
        parse(spec);
    }

    /**
     * Returns a new AccessLogger for the specified log format.
     *
     * @return A AccessLogger.
     */
    public AccessLog newAccessLogger() {
        String[] newElements = new String[elements.length];
        System.arraycopy(elements, 0, newElements, 0, elements.length);
        Map<IndexedLogElement, IndexedLogElement> map = new IdentityHashMap<>();
        return new AccessLog(copy(map, onRequestElements), copy(map, onResponseHeadersElements), copy(map, onResponseWriteElements), copy(map, onLastResponseWriteElements), newElements);
    }

    @Override
    public String toString() {
        SortedSet<IndexedLogElement> elts = new TreeSet<>();
        elts.addAll(constantElements);
        elts.addAll(onLastResponseWriteElements);
        elts.addAll(onRequestElements);
        elts.addAll(onResponseHeadersElements);
        elts.addAll(onResponseWriteElements);
        return elts.stream().map(IndexedLogElement::toString).collect(Collectors.joining());
    }

    private static List<IndexedLogElement> copy(Map<IndexedLogElement, IndexedLogElement> map, List<IndexedLogElement> l) {
        return l.stream().map(elt -> map.computeIfAbsent(elt, IndexedLogElement::copyIndexedLogElement)).collect(Collectors.toList());
    }

    private void parse(String spec) {
        if (spec == null || spec.isEmpty() || "common".equals(spec)) {
            spec = COMMON_LOG_FORMAT;
        } else if ("combined".equals(spec)) {
            spec = COMBINED_LOG_FORMAT;
        }
        List<LogElement> logElements = tokenize(spec);
        elements = new String[logElements.size()];
        for (int i = 0; i < elements.length; ++i) {
            LogElement element = logElements.get(i);
            IndexedLogElement indexedLogElement = new IndexedLogElement(element, i);
            if (element.events().isEmpty()) {
                // constants
                constantElements.add(indexedLogElement);
                // pre-fill log values with constant
                elements[i] = element.onRequestHeaders(null, null, null, null, null);
                continue;
            }
            if (element.events().contains(LogElement.Event.ON_LAST_RESPONSE_WRITE)) {
                onLastResponseWriteElements.add(indexedLogElement);
            }
            if (element.events().contains(LogElement.Event.ON_REQUEST_HEADERS)) {
                onRequestElements.add(indexedLogElement);
            }
            if (element.events().contains(LogElement.Event.ON_RESPONSE_HEADERS)) {
                onResponseHeadersElements.add(indexedLogElement);
            }
            if (element.events().contains(LogElement.Event.ON_RESPONSE_WRITE)) {
                onResponseWriteElements.add(indexedLogElement);
            }
        }
        trimToSize(onLastResponseWriteElements);
        trimToSize(onRequestElements);
        trimToSize(onResponseHeadersElements);
        trimToSize(onResponseWriteElements);
        trimToSize(constantElements);
    }

    private static <T> void trimToSize(List<T> l) {
        ((ArrayList<T>) l).trimToSize();
    }

    private List<LogElement> tokenize(String spec) {
        List<LogElement> logElements = new ArrayList<>();
        spec = spec.trim();
        int state = 0;
        StringBuilder token = new StringBuilder(40);
        for (int i = 0; i < spec.length(); ++i) {
            char c = spec.charAt(i);
            state = nextState(logElements, state, token, c);
        }
        if (state != 0 || logElements.isEmpty()) {
            LOGGER.warn("Invalid access log format: {}", spec);
            throw new IllegalArgumentException("Invalid access log format: " + spec);
        }
        checkConstantElement(logElements, token);
        return logElements;
    }

    private int nextState(List<LogElement> logElements, int state, StringBuilder token, char c) {
        switch (state) {
        case 0:
            // --> spacer
            if (c == '%') {
                state = 1;
            } else {
                token.append(c);
            }
            break;
        case 1:
            // --> %
            if (c == '{') {
                checkConstantElement(logElements, token);
                state = 2;
            } else if (c == '%') {
                // escape literal
                token.append(c);
                state = 0;
            } else {
                checkConstantElement(logElements, token);
                logElements.add(fromToken(Character.toString(c), null));
                state = 0;
            }
            break;
        case 2:
            // --> %{
            if (c == '}') {
                state = 3;
            } else {
                token.append(c);
            }
            break;
        case 3:
            // --> %{<>}
            String param = token.toString();
            logElements.add(fromToken(Character.toString(c), param));
            token.setLength(0);
            state = 0;
            break;
        default:
            // ignore
            break;
        }
        return state;
    }

    private void checkConstantElement(List<LogElement> logElements, StringBuilder token) {
        if (token.length() != 0) {
            logElements.add(new ConstantElement(token.toString()));
            token.setLength(0);
        }
    }

    private LogElement fromToken(String pattern, String param) {
        for (LogElementBuilder builder: LOG_ELEMENT_BUILDERS) {
            LogElement logElement = builder.build(pattern, param);
            if (logElement != null) {
                return logElement;
            }
        }
        LOGGER.warn("Unknown access log marker: %{}", pattern);
        return ConstantElement.UNKNOWN;
    }

    /**
     * A log element with an index that specifies its position in the log format.
     * @author croudet
     */
    static class IndexedLogElement implements LogElement, Comparable<IndexedLogElement> {
        final int index;
        private final LogElement delegate;

        /**
         * Creates an IndexedLogElement.
         * @param delegate A LogElement.
         * @param index The index.
         */
        IndexedLogElement(LogElement delegate, int index) {
            this.delegate = delegate;
            this.index = index;
        }

        @Override
        public Set<Event> events() {
            return delegate.events();
        }

        @Override
        public void reset() {
            delegate.reset();
        }

        @Override
        public String onRequestHeaders(SocketChannel channel, String method, io.netty.handler.codec.http.HttpHeaders headers, String uri, String protocol) {
            return delegate.onRequestHeaders(channel, method, headers, uri, protocol);
        }

        @Override
        public String onResponseHeaders(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpHeaders headers, String status) {
            return delegate.onResponseHeaders(ctx, headers, status);
        }

        @Override
        public void onResponseWrite(int contentSize) {
            delegate.onResponseWrite(contentSize);
        }

        @Override
        public String onLastResponseWrite(int contentSize) {
            return delegate.onLastResponseWrite(contentSize);
        }

        @Override
        public LogElement copy() {
            return new IndexedLogElement(delegate.copy(), index);
        }

        /**
         * Returns a copy of this element.
         * @return A copy of this element.
         */
        public IndexedLogElement copyIndexedLogElement() {
            return new IndexedLogElement(delegate.copy(), index);
        }

        @Override
        public int compareTo(IndexedLogElement o) {
            return Long.compare(index, o.index);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public int hashCode() {
            return index;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            IndexedLogElement other = (IndexedLogElement) obj;
            return index == other.index;
        }

    }
}
