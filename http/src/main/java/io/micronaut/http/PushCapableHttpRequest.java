/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

/**
 * A {@link HttpRequest} that is potentially capable of HTTP2 server push. Code should check
 * {@link #isServerPushSupported()} before attempting to send any server push.
 *
 * @param <B> The Http message body
 * @author Jonas Konrad
 * @since 3.2
 */
@Experimental
public interface PushCapableHttpRequest<B> extends HttpRequest<B> {
    /**
     * Check whether HTTP2 server push is supported by the remote client. Only HTTP2 clients that indicate support
     * through HTTP2 settings have this method return {@code true}.
     *
     * @return {@code true} iff server push is supported.
     */
    @Experimental
    boolean isServerPushSupported();

    /**
     * <p>
     * Initiate a HTTP2 server push for the given request. The information from the given request (i.e. path, headers)
     * will be passed on to the client immediately so that it does not send the request itself. Then, the given request
     * will be handled as if it was initiated by the client, and the response will be passed back to the client.
     * </p>
     *
     * <p>
     * This method mostly follows the semantics of JavaEE {@code javax.servlet.http.PushBuilder}. This means most of
     * the headers of <i>this</i> request are copied into the push request, and the referer is set. To override this
     * behavior, add a corresponding header to the {@code request} passed as the parameter of this method: those
     * headers take precedence.
     * </p>
     *
     * <p>
     * <b>Security note:</b> The {@code Authorization} header and other headers not excluded by the above paragraph
     * will be copied and sent to the client as part of a HTTP2 {@code PUSH_PROMISE}. Normally, this is fine, because
     * the client sent those headers in the first place. But if there is an intermediate proxy that added a header,
     * this header may then leak to the client.
     * </p>
     *
     * @param request The request to respond to using a server push.
     * @return This request.
     * @throws UnsupportedOperationException if the client does not support server push. Check beforehand using
     *                                       {@link #isServerPushSupported()}.
     */
    @Experimental
    PushCapableHttpRequest<B> serverPush(@NonNull HttpRequest<?> request);
}
