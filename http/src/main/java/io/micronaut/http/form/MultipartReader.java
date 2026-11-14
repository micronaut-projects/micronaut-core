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
package io.micronaut.http.form;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.simple.SimpleHttpHeaders;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * A blocking, streaming reader of a {@code multipart/*} body (RFC 2046, RFC 7578) that works on
 * any {@link InputStream} and does not depend on a transport.
 *
 * <p>Parts are read in order with {@link #next()}; the body of a part is streamed by
 * {@link Part#body()} and is only valid until the next call to {@link #next()}, which skips what
 * is left of it. Nothing is buffered beyond one read buffer, so the {@link Limits} bound the
 * memory and the input the reader accepts: the number of parts, the size of the headers of a part,
 * the size of the body of a part and the size of the whole body. Exceeding one throws a
 * {@link ContentLengthExceededException}; a malformed body throws an {@link IOException}.</p>
 *
 * <p>Instances are not thread-safe.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class MultipartReader implements Closeable {

    private static final int MIN_BUFFER_SIZE = 8192;
    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte DASH = '-';

    private final InputStream in;
    private final Limits limits;
    /**
     * {@code CRLF--boundary}.
     */
    private final byte[] delimiter;
    private final byte[] buf;
    private int pos;
    private int limit;
    private boolean eof;
    private long total;
    private int parts;
    private boolean finished;
    private @Nullable PartBody current;

    private MultipartReader(InputStream in, String boundary, Limits limits) {
        if (boundary.isEmpty() || boundary.length() > 70) {
            throw new IllegalArgumentException("Invalid multipart boundary: " + boundary);
        }
        this.in = in;
        this.limits = limits;
        this.delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        this.buf = new byte[Math.max(MIN_BUFFER_SIZE, delimiter.length * 4)];
        // the first delimiter need not be preceded by a line break: start as if it were
        buf[0] = CR;
        buf[1] = LF;
        limit = 2;
    }

    /**
     * Create a reader of a body with the given boundary.
     *
     * @param in       The body
     * @param boundary The boundary (the {@code boundary} parameter of the content type)
     * @param limits   The limits
     * @return The reader
     */
    public static MultipartReader of(InputStream in, String boundary, Limits limits) {
        return new MultipartReader(in, boundary, limits);
    }

    /**
     * Create a reader of a body of the given {@code multipart/*} content type.
     *
     * @param in          The body
     * @param contentType The content type, with a {@code boundary} parameter
     * @param limits      The limits
     * @return The reader
     * @throws IllegalArgumentException if the content type is not multipart or has no boundary
     */
    public static MultipartReader of(InputStream in, MediaType contentType, Limits limits) {
        if (!"multipart".equalsIgnoreCase(contentType.getType())) {
            throw new IllegalArgumentException("Not a multipart content type: " + contentType);
        }
        String boundary = contentType.getParametersMap().get("boundary");
        if (boundary == null) {
            throw new IllegalArgumentException("Missing multipart boundary: " + contentType);
        }
        return of(in, boundary, limits);
    }

    /**
     * Read the next part, skipping the rest of the body of the current one.
     *
     * @return The part, or {@code null} after the last one
     * @throws IOException if reading fails or the body is malformed
     */
    public @Nullable Part next() throws IOException {
        if (finished) {
            return null;
        }
        if (current != null) {
            current.skipRemaining();
            current = null;
        } else {
            // preamble
            skipToDelimiter();
        }
        // after a delimiter: "--" ends the body, otherwise optional whitespace and CRLF
        if (!ensure(2)) {
            throw new IOException("Unexpected end of multipart body after a boundary");
        }
        if (buf[pos] == DASH && buf[pos + 1] == DASH) {
            finished = true;
            return null;
        }
        String line = readLine(limits.maxHeaderSize());
        if (!line.isBlank()) {
            throw new IOException("Unexpected characters after a multipart boundary");
        }
        if (limits.maxParts() >= 0 && ++parts > limits.maxParts()) {
            throw new ContentLengthExceededException("The multipart body has more than " + limits.maxParts() + " parts");
        }
        SimpleHttpHeaders headers = new SimpleHttpHeaders();
        int headerBytes = 0;
        while (true) {
            int remaining = limits.maxHeaderSize() < 0 ? -1 : limits.maxHeaderSize() - headerBytes;
            String header = readLine(remaining);
            headerBytes += header.length() + 2;
            if (header.isEmpty()) {
                break;
            }
            if ((header.charAt(0) == ' ' || header.charAt(0) == '\t')) {
                throw new IOException("Folded multipart header lines are not supported");
            }
            int colon = header.indexOf(':');
            if (colon <= 0) {
                throw new IOException("Malformed multipart header: " + header);
            }
            headers.add(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
        }
        current = new PartBody();
        return new Part(headers, current);
    }

    @Override
    public void close() throws IOException {
        finished = true;
        in.close();
    }

    private void skipToDelimiter() throws IOException {
        while (true) {
            int idx = indexOfDelimiter();
            if (idx >= 0) {
                pos = idx + delimiter.length;
                return;
            }
            pos = Math.max(pos, limit - delimiter.length + 1);
            if (!fill()) {
                throw new IOException("Unexpected end of multipart body: no boundary found");
            }
        }
    }

    /**
     * Index of the delimiter in {@code buf[pos, limit)}, or -1.
     */
    private int indexOfDelimiter() {
        int last = limit - delimiter.length;
        byte first = delimiter[0];
        outer:
        for (int i = pos; i <= last; i++) {
            if (buf[i] != first) {
                continue;
            }
            for (int j = 1; j < delimiter.length; j++) {
                if (buf[i + j] != delimiter[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private boolean ensure(int n) throws IOException {
        while (limit - pos < n) {
            if (!fill()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compact the buffer and read more input.
     *
     * @return false at the end of input
     */
    private boolean fill() throws IOException {
        if (eof) {
            return false;
        }
        if (pos > 0) {
            System.arraycopy(buf, pos, buf, 0, limit - pos);
            limit -= pos;
            pos = 0;
        }
        int n = in.read(buf, limit, buf.length - limit);
        if (n < 0) {
            eof = true;
            return false;
        }
        total += n;
        if (limits.maxTotalSize() >= 0 && total > limits.maxTotalSize()) {
            throw new ContentLengthExceededException("The multipart body exceeds the maximum allowed content length [" + limits.maxTotalSize() + "]");
        }
        limit += n;
        return true;
    }

    private String readLine(int maxBytes) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            if (pos >= limit && !fill()) {
                throw new IOException("Unexpected end of multipart headers");
            }
            byte b = buf[pos++];
            if (b == LF) {
                byte[] bytes = line.toByteArray();
                int len = bytes.length > 0 && bytes[bytes.length - 1] == CR ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, len, StandardCharsets.UTF_8);
            }
            line.write(b);
            if (maxBytes >= 0 && line.size() > maxBytes) {
                throw new ContentLengthExceededException("The headers of a multipart part exceed the maximum allowed size [" + limits.maxHeaderSize() + "]");
            }
        }
    }

    /**
     * The limits of a {@link MultipartReader}. A negative value means unlimited.
     *
     * @param maxParts      The maximum number of parts
     * @param maxHeaderSize The maximum size in bytes of the headers of one part
     * @param maxPartSize   The maximum size in bytes of the body of one part
     * @param maxTotalSize  The maximum size in bytes of the whole multipart body
     */
    public record Limits(int maxParts, int maxHeaderSize, long maxPartSize, long maxTotalSize) {
        /**
         * Defaults: 1000 parts, 8 KiB of headers per part, 1 MiB per part and 10 MiB in total
         * (the defaults of the server's multipart and request size limits).
         */
        public static final Limits DEFAULT = new Limits(1000, 8192, 1024 * 1024, 10 * 1024 * 1024);

        /**
         * No limits.
         */
        public static final Limits UNLIMITED = new Limits(-1, -1, -1, -1);
    }

    /**
     * A part of a multipart body.
     */
    public static final class Part {
        private final HttpHeaders headers;
        private final InputStream body;
        private final @Nullable String name;
        private final @Nullable String fileName;

        private Part(HttpHeaders headers, InputStream body) {
            this.headers = headers;
            this.body = body;
            String disposition = headers.get(HttpHeaders.CONTENT_DISPOSITION);
            String n = null;
            String f = null;
            String fExt = null;
            if (disposition != null) {
                n = dispositionParameter(disposition, "name");
                f = dispositionParameter(disposition, "filename");
                fExt = dispositionParameter(disposition, "filename*");
            }
            this.name = n;
            this.fileName = fExt != null ? decodeExtValue(fExt, f) : f;
        }

        /**
         * @return The headers of the part
         */
        public HttpHeaders headers() {
            return headers;
        }

        /**
         * @return The {@code name} parameter of the {@code Content-Disposition} header, if any
         */
        public @Nullable String name() {
            return name;
        }

        /**
         * @return The {@code filename} (or {@code filename*}) parameter of the {@code Content-Disposition} header, if any
         */
        public @Nullable String fileName() {
            return fileName;
        }

        /**
         * @return The {@code Content-Type} of the part, if any
         */
        public Optional<MediaType> contentType() {
            return headers.contentType();
        }

        /**
         * The body of the part. It ends at the next boundary and is valid until the next call to
         * {@link MultipartReader#next()}. Closing it does not close the reader.
         *
         * @return The body
         */
        public InputStream body() {
            return body;
        }

        private static @Nullable String decodeExtValue(String value, @Nullable String fallback) {
            int first = value.indexOf('\'');
            int second = first < 0 ? -1 : value.indexOf('\'', first + 1);
            if (second < 0) {
                return fallback;
            }
            try {
                Charset charset = Charset.forName(value.substring(0, first));
                return URLDecoder.decode(value.substring(second + 1).replace("+", "%2B"), charset);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }

        /**
         * The value of a parameter of a {@code Content-Disposition} header, unquoted.
         */
        private static @Nullable String dispositionParameter(String header, String parameter) {
            int i = 0;
            int len = header.length();
            // skip the disposition type
            while (i < len && header.charAt(i) != ';') {
                i++;
            }
            while (i < len) {
                i++; // ';'
                int nameStart = i;
                while (i < len && header.charAt(i) != '=' && header.charAt(i) != ';') {
                    i++;
                }
                String paramName = header.substring(nameStart, i).trim().toLowerCase(Locale.ROOT);
                String value = "";
                if (i < len && header.charAt(i) == '=') {
                    i++;
                    while (i < len && header.charAt(i) == ' ') {
                        i++;
                    }
                    StringBuilder sb = new StringBuilder();
                    if (i < len && header.charAt(i) == '"') {
                        i++;
                        while (i < len && header.charAt(i) != '"') {
                            char c = header.charAt(i++);
                            if (c == '\\' && i < len) {
                                c = header.charAt(i++);
                            }
                            sb.append(c);
                        }
                        i++; // closing quote
                        while (i < len && header.charAt(i) != ';') {
                            i++;
                        }
                        value = sb.toString();
                    } else {
                        int valueStart = i;
                        while (i < len && header.charAt(i) != ';') {
                            i++;
                        }
                        value = header.substring(valueStart, i).trim();
                    }
                }
                if (paramName.equals(parameter)) {
                    return value;
                }
            }
            return null;
        }
    }

    /**
     * The body of the current part: the input up to the next delimiter.
     */
    private final class PartBody extends InputStream {
        private boolean done;
        private long size;

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (done || current != this) {
                return -1;
            }
            if (len == 0) {
                return 0;
            }
            while (true) {
                int idx = indexOfDelimiter();
                int available;
                if (idx >= 0) {
                    available = idx - pos;
                    if (available == 0) {
                        pos = idx + delimiter.length;
                        done = true;
                        return -1;
                    }
                } else {
                    // the tail could be the start of a delimiter
                    available = limit - pos - delimiter.length + 1;
                }
                if (available > 0) {
                    int n = Math.min(len, available);
                    System.arraycopy(buf, pos, b, off, n);
                    pos += n;
                    size += n;
                    if (limits.maxPartSize() >= 0 && size > limits.maxPartSize()) {
                        throw new ContentLengthExceededException("A multipart part exceeds the maximum allowed content length [" + limits.maxPartSize() + "]");
                    }
                    return n;
                }
                if (!fill()) {
                    throw new IOException("Unexpected end of multipart body: no closing boundary");
                }
            }
        }

        void skipRemaining() throws IOException {
            byte[] skip = new byte[MIN_BUFFER_SIZE];
            int n;
            do {
                n = read(skip, 0, skip.length);
            } while (n >= 0);
        }

        @Override
        public void close() {
            // does not close the reader; the rest is skipped by next()
        }
    }
}
