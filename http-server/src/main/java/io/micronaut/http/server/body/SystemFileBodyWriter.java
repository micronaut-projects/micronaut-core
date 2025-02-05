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
package io.micronaut.http.server.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.body.stream.InputStreamByteBody;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.MessageBodyException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;

import static io.micronaut.http.HttpHeaders.CONTENT_RANGE;

/**
 * Body writer for {@link SystemFile}s.
 *
 * @author Graeme Rocher
 * @since 4.0.0
 */
@Singleton
@Experimental
@Internal
public final class SystemFileBodyWriter extends AbstractFileBodyWriter implements ResponseBodyWriter<SystemFile> {
    private static final String UNIT_BYTES = "bytes";

    private final ExecutorService ioExecutor;

    public SystemFileBodyWriter(HttpServerConfiguration.FileTypeHandlerConfiguration configuration, @Named(TaskExecutors.BLOCKING) ExecutorService ioExecutor) {
        super(configuration);
        this.ioExecutor = ioExecutor;
    }

    @Override
    public void writeTo(Argument<SystemFile> type, MediaType mediaType, SystemFile file, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        throw new UnsupportedOperationException("Can only be used in a Netty context");
    }

    @Override
    public ByteBodyHttpResponse<?> write(@NonNull ByteBodyFactory bodyFactory,
                                         HttpRequest<?> request,
                                         @NonNull MutableHttpResponse<SystemFile> httpResponse,
                                         @NonNull Argument<SystemFile> type,
                                         @NonNull MediaType mediaType,
                                         SystemFile object) throws CodecException {
        return write(bodyFactory, request, httpResponse, object);
    }

    public ByteBodyHttpResponse<?> write(@NonNull ByteBodyFactory bodyFactory, HttpRequest<?> request, MutableHttpResponse<SystemFile> response, SystemFile systemFile) throws CodecException {
        if (!systemFile.getFile().canRead()) {
            throw new MessageBodyException("Could not find file");
        }
        if (handleIfModifiedAndHeaders(request, response, systemFile, response)) {
            return notModified(bodyFactory, response);
        } else {

            // Parse the range headers (if any), and determine the position and content length
            // Only `bytes` ranges are supported. Only single ranges are supported. Invalid ranges fall back to returning the full response.
            // See https://httpwg.org/specs/rfc9110.html#field.range
            long fileLength = systemFile.getLength();
            long position = 0;
            long contentLength = fileLength;
            if (fileLength > -1) {
                String rangeHeader = request.getHeaders().get(HttpHeaders.RANGE);
                if (rangeHeader != null
                    && request.getMethod() == HttpMethod.GET // A server MUST ignore a Range header field received with a request method that is unrecognized or for which range handling is not defined.
                    && rangeHeader.startsWith(UNIT_BYTES) // An origin server MUST ignore a Range header field that contains a range unit it does not understand.
                    && response.status() == HttpStatus.OK // The Range header field is evaluated after evaluating the precondition header fields defined in Section 13.1, and only if the result in absence of the Range header field would be a 200 (OK) response.
                ) {
                    IntRange range = parseRangeHeader(rangeHeader, fileLength);
                    if (range != null // A server that supports range requests MAY ignore or reject a Range header field that contains an invalid ranges-specifier (Section 14.1.1)
                        && range.firstPos < range.lastPos // A server that supports range requests MAY ignore a Range header field when the selected representation has no content (i.e., the selected representation's data is of zero length).
                        && range.firstPos < fileLength
                        && range.lastPos < fileLength
                    ) {
                        position = range.firstPos;
                        contentLength = range.lastPos + 1 - range.firstPos;
                        response.status(HttpStatus.PARTIAL_CONTENT);
                        response.header(CONTENT_RANGE, "%s %d-%d/%d".formatted(UNIT_BYTES, range.firstPos, range.lastPos, fileLength));
                    }
                }
                response.header(HttpHeaders.ACCEPT_RANGES, UNIT_BYTES);
            }

            File file = systemFile.getFile();
            InputStream is;
            try {
                is = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                throw new MessageBodyException("Could not find file", e);
            }

            @NonNull InputStream stream = new RangeInputStream(is, position, contentLength);
            return ByteBodyHttpResponseWrapper.wrap(response, InputStreamByteBody.create(stream, OptionalLong.of(contentLength), ioExecutor, bodyFactory));
        }
    }

    @Override
    public CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory,
                                        @NonNull HttpRequest<?> request,
                                        @NonNull HttpResponse<?> response,
                                        @NonNull Argument<SystemFile> type,
                                        @NonNull MediaType mediaType,
                                        SystemFile object) {
        return writePiece(bodyFactory, object);
    }

    public @NonNull CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory, SystemFile object) {
        long fileLength = object.getLength();
        InputStream is;
        try {
            is = new FileInputStream(object.getFile());
        } catch (FileNotFoundException e) {
            throw new MessageBodyException("Could not find file", e);
        }
        return InputStreamByteBody.create(is, OptionalLong.of(fileLength), ioExecutor, bodyFactory);
    }

    @Nullable
    private static IntRange parseRangeHeader(String value, long contentLength) {
        int equalsIdx = value.indexOf('=');
        if (equalsIdx < 0 || equalsIdx == value.length() - 1) {
            return null; // Malformed range
        }

        int minusIdx = value.indexOf('-', equalsIdx + 1);
        if (minusIdx < 0) {
            return null; // Malformed range
        }

        String from = value.substring(equalsIdx + 1, minusIdx).trim();
        String to = value.substring(minusIdx + 1).trim();
        try {
            long fromPosition = from.isEmpty() ? 0 : Long.parseLong(from);
            long toPosition = to.isEmpty() ? contentLength - 1 : Long.parseLong(to);
            return new IntRange(fromPosition, toPosition);
        } catch (NumberFormatException e) {
            return null; // Malformed range
        }
    }

    // See https://httpwg.org/specs/rfc9110.html#rule.int-range
    private static class IntRange {
        private final long firstPos;
        private final long lastPos;

        IntRange(long firstPos, long lastPos) {
            this.firstPos = firstPos;
            this.lastPos = lastPos;
        }
    }

    private static final class RangeInputStream extends InputStream {
        private final InputStream delegate;
        private final long toSkip;
        private long remainingLength;
        private boolean skipped = false;
        private boolean skipSuccess = false;

        private RangeInputStream(InputStream delegate, long toSkip, long length) {
            this.delegate = delegate;
            this.toSkip = toSkip;
            this.remainingLength = length;

            if (toSkip == 0) {
                skipped = true;
                skipSuccess = true;
            }
        }

        private boolean doSkip() throws IOException {
            if (!skipped) {
                skipped = true;
                try {
                    delegate.skipNBytes(toSkip);
                    skipSuccess = true;
                } catch (EOFException ignored) {
                }
            }
            return skipSuccess;
        }

        @Override
        public int read() throws IOException {
            if (!doSkip()) {
                return -1;
            }
            if (remainingLength <= 0) {
                return -1;
            }
            int read = delegate.read();
            if (read != -1) {
                remainingLength--;
            }
            return read;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            if (!doSkip()) {
                return -1;
            }
            if (remainingLength <= 0) {
                return -1;
            }
            if (len > remainingLength) {
                len = (int) remainingLength;
            }
            int n = delegate.read(b, off, len);
            if (n != -1) {
                remainingLength -= n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
