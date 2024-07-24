/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * The body reader that reads an object as string.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
@Singleton
@Internal
public final class TextPlainObjectBodyReader implements TypedMessageBodyReader<Object> {

    private final Charset defaultCharset;

    TextPlainObjectBodyReader(ApplicationConfiguration applicationConfiguration) {
        this.defaultCharset = applicationConfiguration.getDefaultCharset();
    }

    @Override
    public Argument<Object> getType() {
        return Argument.OBJECT_ARGUMENT;
    }

    @Override
    public boolean isReadable(Argument<Object> type, MediaType mediaType) {
        return type.getType().equals(Object.class) && mediaType == MediaType.TEXT_PLAIN_TYPE;
    }

    @Override
    public Object read(Argument<Object> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return new String(inputStream.readAllBytes(), MessageBodyWriter.findCharset(mediaType, httpHeaders).orElse(defaultCharset));
        } catch (IOException e) {
            throw new CodecException("Failed to read InputStream", e);
        }
    }

}
