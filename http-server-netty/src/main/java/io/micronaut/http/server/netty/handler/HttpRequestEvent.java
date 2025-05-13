/*
 * Copyright 2017-2025 original authors
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
abstract sealed class HttpRequestEvent extends Event permits Http1RequestEvent, Http2RequestEvent {
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
