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
package io.micronaut.http.client.exceptions;

/**
 * The server reset the stream of the request with an error code that does not say the request
 * was left unprocessed: an HTTP/2 {@code RST_STREAM} other than {@code REFUSED_STREAM}, or an
 * HTTP/3 {@code RESET_STREAM} other than {@code H3_REQUEST_REJECTED}, which are
 * {@link UnprocessedRequestException}s instead. The request may have been processed: whether it
 * can be retried depends on its method.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@SuppressWarnings("java:S110") // the hierarchy depth comes from HttpClientException
public class StreamResetException extends HttpClientException {
    private final long errorCode;
    private final Protocol protocol;

    /**
     * @param errorCode The HTTP/2 error code of the reset
     */
    public StreamResetException(long errorCode) {
        this(errorCode, Protocol.HTTP_2);
    }

    /**
     * @param errorCode The error code of the reset, in the error code space of the protocol
     * @param protocol  The protocol of the stream
     */
    public StreamResetException(long errorCode, Protocol protocol) {
        super("Stream reset by the server with " + protocol.label + " error code 0x" + Long.toHexString(errorCode));
        this.errorCode = errorCode;
        this.protocol = protocol;
    }

    /**
     * @return The error code of the reset, e.g. {@code 0x8} for the HTTP/2 {@code CANCEL}, or
     * {@code 0x10c} for the HTTP/3 {@code H3_REQUEST_CANCELLED}; see {@link #getProtocol()}
     */
    public long getErrorCode() {
        return errorCode;
    }

    /**
     * @return The protocol of the stream, whose error code space {@link #getErrorCode()} is in
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * The protocol of a reset stream: HTTP/2 and HTTP/3 have different error codes.
     */
    public enum Protocol {
        /**
         * HTTP/2, error codes of RFC 9113 section 7.
         */
        HTTP_2("HTTP/2"),
        /**
         * HTTP/3, error codes of RFC 9114 section 8.1.
         */
        HTTP_3("HTTP/3");

        private final String label;

        Protocol(String label) {
            this.label = label;
        }
    }
}
