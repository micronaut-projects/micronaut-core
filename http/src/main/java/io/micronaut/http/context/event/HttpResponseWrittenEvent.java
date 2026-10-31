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
package io.micronaut.http.context.event;

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.jspecify.annotations.Nullable;

/**
 * An event published when the server has finished writing a response.
 *
 * <p>This event fires at most once per request, regardless of transport
 * (HTTP/1.1, HTTP/2, HTTP/3), response type (fixed, streaming), or outcome
 * (success, error, client disconnect).</p>
 *
 * <p>The {@link #getBytesWritten()} value represents the number of
 * response body bytes handed to the network layer after content encoding
 * and compression, excluding HTTP headers and protocol framing. This is
 * the semantically correct value for transfer and bandwidth accounting.
 * When compression is active, the value reflects the compressed size.</p>
 *
 * <p>The {@link #getStatus()} may be {@code null} if the server
 * encountered a fatal error before any response headers were established.</p>
 *
 * <p>Note that this event fires when the server has finished submitting
 * response data to the network layer. It does not guarantee that the
 * client has received or acknowledged the data.</p>
 *
 * <p>Listeners are invoked on the thread the server selects for request handling, which with the
 * default {@code micronaut.server.thread-selection} ({@code MANUAL}, and also with {@code AUTO})
 * is the Netty event loop of the connection. A listener that blocks or does slow work delays every
 * other connection on that event loop. Listeners that need to block must hand off, for example with
 * {@code @Async} on the {@code @EventListener} method, or the server can be configured with
 * {@code micronaut.server.thread-selection=BLOCKING} which moves the listeners (and controllers)
 * off the event loop.</p>
 *
 * @author Vinit Shinde
 * @since 5.3.0
 */
public final class HttpResponseWrittenEvent extends ApplicationEvent {

    private final @Nullable HttpStatus status;
    private final long bytesWritten;

    /**
     * Create a new event.
     *
     * @param request      The request that produced this response. Never null.
     * @param status       The response status, or {@code null} when no response was established
     * @param bytesWritten The number of response body bytes written (post-compression)
     */
    public HttpResponseWrittenEvent(HttpRequest<?> request,
                                    @Nullable HttpStatus status,
                                    long bytesWritten) {
        super(request);
        this.status = status;
        this.bytesWritten = bytesWritten;
    }

    /**
     * Returns the request associated with this response.
     *
     * @return The request
     */
    @Override
    public HttpRequest<?> getSource() {
        return (HttpRequest<?>) super.getSource();
    }


    /**
     * Returns the HTTP response status.
     *
     * @return The HTTP response status, or {@code null} if the status could not be determined
     *         due to a fatal error before the response headers were available, or if the response
     *         uses an unrecognized or custom status code.
     */
    public @Nullable HttpStatus getStatus() {
        return status;
    }

    /**
     * Returns the number of response body bytes written. This value represents
     * post-compression body bytes, excluding HTTP headers and protocol framing.
     * When no compression is active, this equals the serialized body size.
     *
     * <p>For streaming responses, this is the accumulated total of all chunks.
     * For partial responses (errors or client disconnects during streaming),
     * this reflects only the bytes that were successfully submitted.</p>
     *
     * @return The bytes written, always {@code >= 0}
     */
    public long getBytesWritten() {
        return bytesWritten;
    }
}
