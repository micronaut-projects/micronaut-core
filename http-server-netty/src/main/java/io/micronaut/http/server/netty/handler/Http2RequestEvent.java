package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import jdk.jfr.Description;

/**
 * JFR event that tracks HTTP/2 request lifetime.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
@Description("JFR event that tracks HTTP/2 request lifetime.")
final class Http2RequestEvent extends HttpRequestEvent {
    private static final Http2RequestEvent EVENT = new Http2RequestEvent();

    int streamId;

    static boolean isTurnedOn() {
        return EVENT.isEnabled();
    }
}
