package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.StackTrace;

/**
 * JFR event that tracks HTTP request lifetime.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
@StackTrace(false)
@Enabled(false)
abstract class HttpRequestEvent extends Event {
    String channelId;
    String method;
    String uri;
    int status;

    void populateChannel(Channel channel) {
        channelId = channel.id().asLongText();
    }

    void populateRequest(HttpRequest request) {
        method = request.method().name();
        uri = request.uri();
    }

    void populateResponse(HttpResponse response) {
        status = response.status().code();
    }
}
