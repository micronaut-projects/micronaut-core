/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.types.files;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.exceptions.MessageBodyException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;

/**
 * A special type for streaming an {@link InputStream} representing a file or resource.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class StreamedFile implements FileCustomizableResponseType {
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final MediaType mediaType;
    private final String name;
    private final long lastModified;
    private final InputStream inputStream;
    private final long length;
    private String attachmentName;

    /**
     * @param inputStream The input stream
     * @param mediaType   The media type of the content
     */
    public StreamedFile(InputStream inputStream, MediaType mediaType) {
        this(inputStream, mediaType, Instant.now().toEpochMilli());
    }

    /**
     * @param inputStream  The input stream
     * @param mediaType    The media type of the content
     * @param lastModified The last modified date
     */
    public StreamedFile(InputStream inputStream, MediaType mediaType, long lastModified) {
        this(inputStream, mediaType, lastModified, -1);
    }

    /**
     * @param inputStream   The input stream
     * @param mediaType     The media type of the content
     * @param lastModified  The last modified date
     * @param contentLength the content length
     */
    public StreamedFile(InputStream inputStream, MediaType mediaType, long lastModified, long contentLength) {
        this.mediaType = mediaType;
        this.name = null;
        this.lastModified = lastModified;
        this.inputStream = inputStream;
        this.length = contentLength;
    }

    /**
     * Immediately opens a connection to the given URL to retrieve
     * data about the connection, including the input stream.
     *
     * @param url The URL to resource
     */
    public StreamedFile(URL url) {
        String path = url.getPath();
        int idx = path.lastIndexOf(File.separatorChar);
        this.name = idx > -1 ? path.substring(idx + 1) : path;
        this.mediaType = MediaType.forFilename(name);
        try {
            URLConnection con = url.openConnection();
            this.lastModified = con.getLastModified();
            this.inputStream = con.getInputStream();
            this.length = con.getContentLengthLong();
        } catch (IOException e) {
            throw new MessageBodyException("Could not open a connection to the URL: " + path, e);
        }
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public MediaType getMediaType() {
        return mediaType;
    }

    /**
     * @return The stream used to retrieve data for the file
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * Sets the file to be downloaded as an attachment.
     * The name is set in the Content-Disposition header.
     *
     * @param attachmentName The attachment name.
     * @return The same StreamedFile instance
     */
    public StreamedFile attach(String attachmentName) {
        this.attachmentName = attachmentName;
        return this;
    }

    @Override
    public void process(MutableHttpResponse<?> response) {
        if (attachmentName != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentHeader(attachmentName));
        }
    }

    static String buildAttachmentHeader(String attachmentName) {
        // https://httpwg.org/specs/rfc6266.html#advice.generating
        // 'filename' parameter is the fallback for legacy browsers, 'filename*' is the supported approach.
        return "attachment; filename=\"" + sanitizeAscii(attachmentName) + "\"; filename*=utf-8''" + encodeRfc6987(attachmentName);
    }

    private static String sanitizeAscii(String s) {
        StringBuilder builder = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // " ends the string
            if (c >= 32 && c < 127 && c != '"') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    // this is mostly copied from netty QueryStringEncoder

    @SuppressWarnings({"java:S3776", "java:S135", "java:S127"}) // stay close to netty impl
    static String encodeRfc6987(String s) {
        StringBuilder uriBuilder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (dontNeedEncoding(c)) {
                    uriBuilder.append(c);
                } else {
                    appendEncoded(uriBuilder, c);
                }
            } else if (c < 0x800) {
                appendEncoded(uriBuilder, 0xc0 | (c >> 6));
                appendEncoded(uriBuilder, 0x80 | (c & 0x3f));
            } else if (Character.isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    appendEncoded(uriBuilder, '?');
                    continue;
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == s.length()) {
                    appendEncoded(uriBuilder, '?');
                    break;
                }
                // Extra method to allow inlining the rest of writeUtf8 which is the most likely code path.
                writeUtf8Surrogate(uriBuilder, c, s.charAt(i));
            } else {
                appendEncoded(uriBuilder, 0xe0 | (c >> 12));
                appendEncoded(uriBuilder, 0x80 | ((c >> 6) & 0x3f));
                appendEncoded(uriBuilder, 0x80 | (c & 0x3f));
            }
        }
        return uriBuilder.toString();
    }

    private static boolean dontNeedEncoding(char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9'
                || ch == '-' || ch == '_' || ch == '.' || ch == '*' || ch == '~';
    }

    private static void appendEncoded(StringBuilder uriBuilder, int b) {
        uriBuilder.append('%').append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
    }

    private static void writeUtf8Surrogate(StringBuilder uriBuilder, char c, char c2) {
        if (!Character.isLowSurrogate(c2)) {
            appendEncoded(uriBuilder, '?');
            appendEncoded(uriBuilder, Character.isHighSurrogate(c2) ? '?' : c2);
            return;
        }
        int codePoint = Character.toCodePoint(c, c2);
        // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
        appendEncoded(uriBuilder, 0xf0 | (codePoint >> 18));
        appendEncoded(uriBuilder, 0x80 | ((codePoint >> 12) & 0x3f));
        appendEncoded(uriBuilder, 0x80 | ((codePoint >> 6) & 0x3f));
        appendEncoded(uriBuilder, 0x80 | (codePoint & 0x3f));
    }
}
