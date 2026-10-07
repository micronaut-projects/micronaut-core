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
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

/**
 * What is decided about one attempt of an exchange before anything is sent: the request, how its
 * body is framed, whether it waits for {@code 100 Continue}, the protocols it may switch to and
 * whether it can be sent again on another connection. It holds no resource, see
 * {@link ClientExchange} for those.
 *
 * @param request           The request
 * @param nettyRequest      The netty request whose head is sent. Its framing and
 *                          {@code Connection} headers are set when the exchange starts
 * @param selection         The selection of the load balancer, or {@code null}
 * @param streamed          Whether the body is streamed, as opposed to available in memory
 * @param length            The length of the body, or empty for the chunked transfer coding
 * @param expectContinue    Whether the body is held back until the server answers
 *                          {@code Expect: 100-continue}
 * @param retryEligible     Whether the attempt may end with a {@link NettyHttpClient.StaleConnectionException}
 *                          so that the request is sent again on another connection. The caller
 *                          allows it only for an idempotent request with an available body on a
 *                          reused HTTP/1 connection, and never for a request that expects
 *                          {@code 100 Continue}: the server may have processed it already
 * @param requestedUpgrade  The protocols the request offers to switch to, from all its
 *                          {@code Upgrade} field lines, or {@code null} if it may not switch
 * @param skipDecompression Whether the response body is passed on compressed
 * @param uploadListener    Notified when the body upload starts and ends, or {@code null}
 * @param readIdleTimeout   The read idle timeout of this exchange, or {@code null}
 * @param activityTimeout   The activity timeout of a switched connection, or {@code null}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record ExchangePlan(
    io.micronaut.http.HttpRequest<?> request,
    HttpRequest nettyRequest,
    @Nullable LoadBalancerSelection selection,
    boolean streamed,
    OptionalLong length,
    boolean expectContinue,
    boolean retryEligible,
    @Nullable String requestedUpgrade,
    boolean skipDecompression,
    NettyHttpClient.@Nullable UploadListener uploadListener,
    @Nullable Duration readIdleTimeout,
    @Nullable Duration activityTimeout
) {

    /**
     * Plan an attempt.
     *
     * @param request      The request
     * @param nettyRequest The netty request to send
     * @param selection    The selection of the load balancer, or {@code null}
     * @param byteBody     The request body
     * @param allowRetry   Whether the caller allows sending the request again on another
     *                     connection, see {@link #retryEligible()}
     * @return The plan
     */
    static ExchangePlan of(
        io.micronaut.http.HttpRequest<?> request,
        HttpRequest nettyRequest,
        @Nullable LoadBalancerSelection selection,
        CloseableByteBody byteBody,
        boolean allowRetry
    ) {
        boolean expectContinue = HttpUtil.is100ContinueExpected(nettyRequest);
        return new ExchangePlan(
            request,
            nettyRequest,
            selection,
            !(byteBody instanceof AvailableByteBody),
            // a body whose trailers are known, e.g. a relayed body that was received fully before
            // it is sent on, may have a known length. The trailers need the chunked transfer
            // coding: a Content-Length request would drop them
            NettyByteBodyFactory.hasTrailers(byteBody) ? OptionalLong.empty() : byteBody.expectedLength(),
            expectContinue,
            // a request that expects 100-continue may already have been processed when the
            // connection fails, so it is never sent again
            allowRetry && !expectContinue,
            request.getAttribute(NettyHttpClient.ALLOW_UPGRADE).isPresent() ? joinedValues(nettyRequest.headers(), HttpHeaderNames.UPGRADE) : null,
            request.getAttribute(NettyHttpClient.NO_DECOMPRESSION).isPresent(),
            request.getAttribute(NettyHttpClient.UPLOAD_LISTENER, NettyHttpClient.UploadListener.class).orElse(null),
            request.getAttribute(NettyHttpClient.READ_IDLE_TIMEOUT, Duration.class).orElse(null),
            request.getAttribute(NettyHttpClient.ACTIVITY_TIMEOUT, Duration.class).orElse(null)
        );
    }

    /**
     * @return The values of all field lines of a header, joined as one list, or {@code null} if
     * there is none
     */
    static @Nullable String joinedValues(io.netty.handler.codec.http.HttpHeaders headers, CharSequence name) {
        List<String> values = headers.getAll(name);
        return values.isEmpty() ? null : String.join(",", values);
    }
}
