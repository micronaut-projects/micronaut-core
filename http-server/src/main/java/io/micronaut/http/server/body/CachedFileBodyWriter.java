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
package io.micronaut.http.server.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.types.files.CachedFile;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes a {@link CachedFile} from memory, on the calling thread.
 *
 * @since 5.3.0
 */
@Singleton
@Experimental
@Internal
public final class CachedFileBodyWriter extends AbstractFileBodyWriter implements ResponseBodyWriter<CachedFile> {

    CachedFileBodyWriter(HttpServerConfiguration.FileTypeHandlerConfiguration configuration) {
        super(configuration);
    }

    @Override
    public ByteBodyHttpResponse<?> write(ByteBodyFactory bodyFactory,
                                         HttpRequest<?> request,
                                         MutableHttpResponse<CachedFile> outgoingResponse,
                                         Argument<CachedFile> type,
                                         MediaType mediaType,
                                         CachedFile object) throws CodecException {
        if (handleIfModifiedAndHeaders(request, outgoingResponse, object, outgoingResponse)) {
            return notModified(bodyFactory, outgoingResponse);
        }
        return ByteBodyHttpResponseWrapper.wrap(outgoingResponse, bodyFactory.adapt(object.content()));
    }

    @Override
    public CloseableByteBody writePiece(ByteBodyFactory bodyFactory,
                                        HttpRequest<?> request,
                                        HttpResponse<?> response,
                                        Argument<CachedFile> type,
                                        MediaType mediaType,
                                        CachedFile object) {
        return bodyFactory.adapt(object.content());
    }

    @Override
    public void writeTo(Argument<CachedFile> type, MediaType mediaType, CachedFile object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        try {
            outputStream.write(object.content());
        } catch (IOException e) {
            throw new CodecException("Error writing a cached file", e);
        }
    }
}
