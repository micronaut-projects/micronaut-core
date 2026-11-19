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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.CaseInsensitiveMutableHttpHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * {@link ContentLengthExceededException}; a malformed body throws a
 * {@link MalformedMultipartException}.</p>
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
    /**
     * The maximum transport padding after a boundary. Composers must not generate any.
     */
    private static final int MAX_TRANSPORT_PADDING = 256;

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
    /**
     * The bytes consumed by the last {@link #readLine(int)}, line terminator included.
     */
    private int lineBytes;
    /**
     * The cached result of {@link #indexOfDelimiter()} for the current buffer, or -2 if unknown.
     * Valid until the next {@link #fill()}, as long as {@link #pos} does not pass it.
     */
    private int delimiterIndex = -2;

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
     * @throws IOException if reading fails, or a {@link MalformedMultipartException} if the body is malformed
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
        // after a delimiter: "--" ends the body, then optional transport padding and CRLF
        if (ensure(2) && buf[pos] == DASH && buf[pos + 1] == DASH) {
            pos += 2;
            skipDelimiterLine(true);
            finished = true;
            return null;
        }
        skipDelimiterLine(false);
        if (limits.maxParts() >= 0 && ++parts > limits.maxParts()) {
            throw new ContentLengthExceededException("The multipart body has more than " + limits.maxParts() + " parts");
        }
        // the values are raw (UTF-8) header text: store them without validation
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(false, ConversionService.SHARED);
        int headerBytes = 0;
        @Nullable String headerName = null;
        StringBuilder headerValue = new StringBuilder();
        while (true) {
            int remaining = limits.maxHeaderSize() < 0 ? -1 : Math.max(0, limits.maxHeaderSize() - headerBytes);
            String header = readLine(remaining);
            headerBytes += lineBytes;
            if (!header.isEmpty() && (header.charAt(0) == ' ' || header.charAt(0) == '\t')) {
                // obs-fold (RFC 5322 2.2.3): unfold by removing the line break
                if (headerName == null) {
                    throw new MalformedMultipartException("Multipart header continuation line without a header");
                }
                checkHeaderLine(header);
                headerValue.append(header);
            } else {
                if (headerName != null) {
                    headers.add(headerName, headerValue.toString().trim());
                }
                if (header.isEmpty()) {
                    break;
                }
                int colon = header.indexOf(':');
                if (colon <= 0) {
                    throw new MalformedMultipartException("Malformed multipart header: " + header);
                }
                headerName = header.substring(0, colon);
                if (!isToken(headerName)) {
                    throw new MalformedMultipartException("Invalid multipart header name: " + headerName);
                }
                checkHeaderLine(header);
                headerValue.setLength(0);
                headerValue.append(header, colon + 1, header.length());
            }
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
            pos = contentEnd();
            if (!fill()) {
                throw new MalformedMultipartException("Unexpected end of multipart body: no boundary found");
            }
        }
    }

    /**
     * When {@link #indexOfDelimiter()} found no delimiter, the end of the bytes in the buffer
     * that cannot be the start of a delimiter.
     */
    private int contentEnd() {
        // the tail could be the start of a delimiter
        return Math.max(pos, limit - delimiter.length + 1);
    }

    /**
     * Index of the delimiter in {@code buf[pos, limit)}, or -1.
     */
    private int indexOfDelimiter() {
        if (delimiterIndex == -2 || (delimiterIndex >= 0 && delimiterIndex < pos)) {
            delimiterIndex = scanDelimiter();
        }
        return delimiterIndex;
    }

    private int scanDelimiter() {
        int last = limit - delimiter.length;
        byte first = delimiter[0];
        for (int i = pos; i <= last; i++) {
            if (buf[i] == first && Arrays.equals(buf, i, i + delimiter.length, delimiter, 0, delimiter.length)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Skip the rest of the line of a delimiter (RFC 2046 5.1.1): optional transport padding
     * (LWSP, at most {@link #MAX_TRANSPORT_PADDING} bytes) and a line break, or, after a close
     * delimiter, the end of the input. A boundary at the start of a line is always a delimiter,
     * so anything else is malformed.
     */
    private void skipDelimiterLine(boolean close) throws IOException {
        int padding = 0;
        while (true) {
            if (!ensure(1)) {
                if (close) {
                    return;
                }
                throw new MalformedMultipartException("Unexpected end of multipart body after a boundary");
            }
            byte b = buf[pos];
            if (b == ' ' || b == '\t') {
                if (++padding > MAX_TRANSPORT_PADDING) {
                    throw new MalformedMultipartException("Multipart boundary transport padding is too long");
                }
                pos++;
            } else if (b == LF) {
                pos++;
                return;
            } else if (b == CR) {
                if (!ensure(2) || buf[pos + 1] != LF) {
                    throw new MalformedMultipartException("Unexpected characters after a multipart boundary");
                }
                pos += 2;
                return;
            } else {
                throw new MalformedMultipartException("Unexpected characters after a multipart boundary");
            }
        }
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
        delimiterIndex = -2;
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

    /**
     * Read a line and its terminator. The bytes consumed, terminator included, are stored in
     * {@link #lineBytes} and may not exceed {@code maxBytes} (unless it is negative).
     */
    private String readLine(int maxBytes) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int consumed = 0;
        while (true) {
            if (maxBytes >= 0 && consumed >= maxBytes) {
                throw new ContentLengthExceededException("The headers of a multipart part exceed the maximum allowed size [" + limits.maxHeaderSize() + "]");
            }
            if (pos >= limit && !fill()) {
                throw new MalformedMultipartException("Unexpected end of multipart headers");
            }
            byte b = buf[pos++];
            consumed++;
            if (b == LF) {
                lineBytes = consumed;
                byte[] bytes = line.toByteArray();
                int len = bytes.length > 0 && bytes[bytes.length - 1] == CR ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, len, StandardCharsets.UTF_8);
            }
            line.write(b);
        }
    }

    /**
     * Reject CR and the other control characters except HTAB in a header line: a bare CR does not
     * end a line, and must not reach a header value.
     */
    private static void checkHeaderLine(String line) throws MalformedMultipartException {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7F) {
                throw new MalformedMultipartException("Invalid character in a multipart header");
            }
        }
    }

    /**
     * Whether the name is an RFC 9110 token.
     */
    private static boolean isToken(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
            if (!valid) {
                return false;
            }
        }
        return !name.isEmpty();
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
        public static final Limits DEFAULT = new Limits(1000, 8192, 1024L * 1024, 10L * 1024 * 1024);

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
            if (availableBodyBytes() <= 0) {
                return -1;
            }
            int b = buf[pos++] & 0xFF;
            count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            int available = availableBodyBytes();
            if (available <= 0) {
                return -1;
            }
            int n = Math.min(len, available);
            System.arraycopy(buf, pos, b, off, n);
            pos += n;
            count(n);
            return n;
        }

        /**
         * The number of bytes of the body available in the buffer, reading more input if there
         * are none, or -1 at the end of the body (after consuming the delimiter).
         */
        private int availableBodyBytes() throws IOException {
            if (done || current != this) {
                return -1;
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
                    available = contentEnd() - pos;
                }
                if (available > 0) {
                    return available;
                }
                if (!fill()) {
                    throw new MalformedMultipartException("Unexpected end of multipart body: no closing boundary");
                }
            }
        }

        private void count(int n) {
            size += n;
            if (limits.maxPartSize() >= 0 && size > limits.maxPartSize()) {
                throw new ContentLengthExceededException("A multipart part exceeds the maximum allowed content length [" + limits.maxPartSize() + "]");
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
