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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.exceptions.StreamResetException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.netty.handler.codec.http3.Http3ErrorCode;
import io.netty.handler.codec.quic.QuicStreamResetException;

/**
 * Maps the reset of an HTTP/3 request stream ({@code RESET_STREAM}), which QUIC reports as a
 * {@link QuicStreamResetException} with the error code: a rejected request was not processed
 * (RFC 9114 section 4.1.1), so the request can be sent again; any other reset may have been
 * processed. A class of its own, so that the optional QUIC classes are only loaded for HTTP/3.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class Http3StreamReset {
    private Http3StreamReset() {
    }

    /**
     * @param cause A failure of an HTTP/3 request stream
     * @return The failure of the request, if the stream was reset, else the cause
     */
    static Throwable map(Throwable cause) {
        if (!(cause instanceof QuicStreamResetException reset)) {
            return cause;
        }
        long errorCode = reset.applicationProtocolCode();
        Throwable failure;
        if (errorCode == Http3ErrorCode.H3_REQUEST_REJECTED.code()) {
            failure = new UnprocessedRequestException(UnprocessedRequestException.Reason.STREAM_REFUSED, "Stream refused by the server (H3_REQUEST_REJECTED)", cause);
        } else {
            failure = new StreamResetException(errorCode, StreamResetException.Protocol.HTTP_3).initCause(cause);
        }
        return failure;
    }
}
