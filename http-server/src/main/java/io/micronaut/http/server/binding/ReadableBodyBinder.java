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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.io.Readable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * Binds a {@code @Body Readable} argument to the body of the request, read as it arrives. The stream of the
 * {@link Readable} is the one of a {@code @Body InputStream} (see {@link InputStreamBodyBinder}), so a read past
 * the maximum request size fails with the {@link io.micronaut.http.exceptions.ContentLengthExceededException}, and
 * its reader decodes the body with the character encoding of the request.
 *
 * @since 5.3.0
 */
@Internal
public final class ReadableBodyBinder implements NonBlockingBodyArgumentBinder<Readable> {

    public static final Argument<Readable> TYPE = Argument.of(Readable.class);

    private final InputStreamBodyBinder inputStreamBodyBinder;

    /**
     * @param inputStreamBodyBinder The binder of the stream of the body
     */
    public ReadableBodyBinder(InputStreamBodyBinder inputStreamBodyBinder) {
        this.inputStreamBodyBinder = inputStreamBodyBinder;
    }

    @Override
    public Argument<Readable> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<Readable> bind(ArgumentConversionContext<Readable> context, HttpRequest<?> source) {
        Optional<InputStream> stream = inputStreamBodyBinder
            .bind(ConversionContext.of(InputStreamBodyBinder.TYPE), source)
            .getValue();
        if (stream.isEmpty()) {
            return BindingResult.empty();
        }
        Readable readable = new BodyReadable(stream.get(), source.getCharacterEncoding(), source.getPath());
        return () -> Optional.of(readable);
    }

    /**
     * The body of the request.
     *
     * @param stream  The stream of the body
     * @param charset The character encoding of the request
     * @param name    The path of the request
     */
    private record BodyReadable(InputStream stream, Charset charset, String name) implements Readable {

        @NonNull
        @Override
        public InputStream asInputStream() {
            return stream;
        }

        @Override
        public Reader asReader() throws IOException {
            return asReader(charset);
        }

        @Override
        public boolean exists() {
            return true;
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }
    }
}
