package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import jdk.jfr.Description;

/**
 * JFR event that tracks HTTP/1.x request lifetime.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
@Description("JFR event that tracks HTTP/1.x request lifetime.")
final class Http1RequestEvent extends HttpRequestEvent {
    private static final Http1RequestEvent EVENT = new Http1RequestEvent();

    static boolean isTurnedOn() {
        return EVENT.isEnabled();
    }
}
