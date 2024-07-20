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
package io.micronaut.json.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.TextPlainBodyHandler;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A JSON body should not be escaped or parsed as a JSON value.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Singleton
@JsonMessageHandler.ProducesJson
@JsonMessageHandler.ConsumesJson
@Internal
public final class RawJsonStringHandler implements MessageBodyWriter<CharSequence>, MessageBodyReader<String> {

    private final TextPlainBodyHandler textPlainBodyHandler = new TextPlainBodyHandler();

    @Override
    public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        return textPlainBodyHandler.read(type, mediaType, httpHeaders, inputStream);
    }

    @Override
    public void writeTo(Argument<CharSequence> type, MediaType mediaType, CharSequence object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        textPlainBodyHandler.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }
}
