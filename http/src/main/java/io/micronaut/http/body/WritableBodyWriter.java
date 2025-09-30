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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Body writer for {@link Writable}s.
 *
 * @since 4.0.0
 * @author Graeme Rocher
 */
@Singleton
@Experimental
@BootstrapContextCompatible
public final class WritableBodyWriter implements TypedMessageBodyHandler<Writable>, ChunkedMessageBodyReader<Writable>, ResponseBodyWriter<Writable> {

    private final ApplicationConfiguration applicationConfiguration;

    public WritableBodyWriter(ApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
    }

    @Override
    public Argument<Writable> getType() {
        return Argument.of(Writable.class);
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public void writeTo(Argument<Writable> type, MediaType mediaType, Writable object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        if (mediaType != null) {
            outgoingHeaders.setIfMissing(HttpHeaders.CONTENT_TYPE, mediaType);
        }
        try {
            object.writeTo(outputStream, MessageBodyWriter.getCharset(mediaType, outgoingHeaders));
            outputStream.flush();
        } catch (IOException e) {
            throw new CodecException("Error writing body text: " + e.getMessage(), e);
        }
    }

    private Writable read0(ByteBuffer<?> byteBuffer) {
        String s = byteBuffer.toString(applicationConfiguration.getDefaultCharset());
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return w -> w.write(s);
    }

    @Override
    public Publisher<? extends Writable> readChunked(Argument<Writable> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return Flux.from(input).map(this::read0);
    }

    @Override
    public Writable read(Argument<Writable> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return read0(byteBuffer);
    }

    @Override
    public Writable read(Argument<Writable> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        String s;
        try {
            s = new String(inputStream.readAllBytes(), applicationConfiguration.getDefaultCharset());
        } catch (IOException e) {
            throw new CodecException("Failed to read InputStream", e);
        }
        return w -> w.write(s);
    }

    @Override
    public @NonNull CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory, @NonNull HttpRequest<?> request, @NonNull HttpResponse<?> response, @NonNull Argument<Writable> type, @NonNull MediaType mediaType, Writable object) throws CodecException {
        try {
            return bodyFactory.buffer(o -> object.writeTo(o, MessageBodyWriter.getCharset(mediaType, response.getHeaders())));
        } catch (IOException e) {
            throw new CodecException("Failed to buffer Writable", e);
        }
    }
}
