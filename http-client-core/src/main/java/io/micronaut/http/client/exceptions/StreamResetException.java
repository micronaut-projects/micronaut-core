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
 * The server reset the HTTP/2 stream of the request ({@code RST_STREAM}) with an error code other
 * than {@code REFUSED_STREAM}, which is an {@link UnprocessedRequestException} instead. The
 * request may have been processed: whether it can be retried depends on its method.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@SuppressWarnings("java:S110") // the hierarchy depth comes from HttpClientException
public class StreamResetException extends HttpClientException {
    private final long errorCode;

    /**
     * @param errorCode The HTTP/2 error code of the reset
     */
    public StreamResetException(long errorCode) {
        super("Stream reset by the server with error code " + errorCode);
        this.errorCode = errorCode;
    }

    /**
     * @return The HTTP/2 error code of the reset, e.g. {@code 0x8} for {@code CANCEL}
     */
    public long getErrorCode() {
        return errorCode;
    }
}
