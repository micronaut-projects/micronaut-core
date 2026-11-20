/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The conversion contexts of the arguments of one binding pass of a request. The locale and the character encoding
 * are read from the request when a conversion first needs them, once for all the arguments of the pass: most
 * conversions of request values need neither.
 */
final class RequestConversionContexts {

    private final HttpRequest<?> request;
    private @Nullable Locale locale;
    private @Nullable Charset charset;

    RequestConversionContexts(HttpRequest<?> request) {
        this.request = request;
    }

    <E> ArgumentConversionContext<E> newContext(Argument<E> argument) {
        return new RequestArgumentConversionContext<>(argument, this);
    }

    /**
     * Reads the locale and the character encoding from the request now.
     */
    void resolve() {
        locale();
        charset();
    }

    Locale locale() {
        Locale l = locale;
        if (l == null) {
            l = request.getLocale().orElseGet(Locale::getDefault);
            locale = l;
        }
        return l;
    }

    Charset charset() {
        Charset c = charset;
        if (c == null) {
            c = Objects.requireNonNullElse(request.getCharacterEncoding(), StandardCharsets.UTF_8);
            charset = c;
        }
        return c;
    }

    /**
     * The conversion context of an argument, like the one of
     * {@link io.micronaut.core.convert.ConversionContext#of(Argument, Locale, Charset)}.
     *
     * @param <T> The argument type
     */
    private static final class RequestArgumentConversionContext<T> implements ArgumentConversionContext<T> {

        private final Argument<T> argument;
        private final RequestConversionContexts contexts;
        private @Nullable ArgumentConversionContext<T> errors;

        RequestArgumentConversionContext(Argument<T> argument, RequestConversionContexts contexts) {
            this.argument = argument;
            this.contexts = contexts;
        }

        @Override
        public Argument<T> getArgument() {
            return argument;
        }

        @Override
        public Locale getLocale() {
            return contexts.locale();
        }

        @Override
        public Charset getCharset() {
            return contexts.charset();
        }

        @Override
        public void reject(Exception exception) {
            errors().reject(exception);
        }

        @Override
        public void reject(Object value, Exception exception) {
            errors().reject(value, exception);
        }

        /**
         * The conversion errors are kept by a context of the argument that is created with the first error.
         *
         * @return The context that keeps the conversion errors
         */
        private ArgumentConversionContext<T> errors() {
            ArgumentConversionContext<T> e = errors;
            if (e == null) {
                e = ConversionContext.of(argument);
                errors = e;
            }
            return e;
        }

        @Override
        public Optional<ConversionError> getLastError() {
            ArgumentConversionContext<T> e = errors;
            return e == null ? Optional.empty() : e.getLastError();
        }

        @Override
        public Iterator<ConversionError> iterator() {
            ArgumentConversionContext<T> e = errors;
            return e == null ? Collections.emptyIterator() : e.iterator();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RequestArgumentConversionContext<?> that = (RequestArgumentConversionContext<?>) o;
            return Objects.equals(argument, that.argument)
                && Objects.equals(getLocale(), that.getLocale())
                && Objects.equals(getCharset(), that.getCharset());
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(argument, getLocale(), getCharset());
        }

        @Override
        public String toString() {
            return argument.toString();
        }
    }
}
