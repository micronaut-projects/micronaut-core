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
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Body writer for {@link CharSequence}s.
 *
 * @author Graeme Rocher
 * @since 4.0.0
 */
@Singleton
@Internal
public final class CharSequenceBodyWriter implements TypedMessageBodyWriter<CharSequence> {

    private final Charset defaultCharset;

    @Inject
    CharSequenceBodyWriter(ApplicationConfiguration applicationConfiguration) {
        this(applicationConfiguration.getDefaultCharset());
    }

    public CharSequenceBodyWriter(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    @Override
    public void writeTo(Argument<CharSequence> type, MediaType mediaType, CharSequence object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        if (mediaType != null) {
            outgoingHeaders.setIfMissing(HttpHeaders.CONTENT_TYPE, mediaType);
        }
        try {
            outputStream.write(object.toString().getBytes(MessageBodyWriter.findCharset(mediaType, outgoingHeaders).orElse(defaultCharset)));
        } catch (IOException e) {
            throw new CodecException("Error writing body text: " + e.getMessage(), e);
        }
    }

    @Override
    public Argument<CharSequence> getType() {
        return Argument.of(CharSequence.class);
    }
}
