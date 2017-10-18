/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.configuration.jackson.server.http;

import org.particleframework.http.MediaType;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.HttpContentSubscriber;
import org.particleframework.http.server.netty.HttpContentSubscriberFactory;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.annotation.Consumes;
import org.reactivestreams.Subscriber;

import javax.inject.Singleton;

/**
 * Builds the {@link Subscriber} for JSON requests
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
public class JsonHttpContentSubscriberFactory implements HttpContentSubscriberFactory {

    private final HttpServerConfiguration httpServerConfiguration;

    public JsonHttpContentSubscriberFactory(HttpServerConfiguration httpServerConfiguration) {
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public HttpContentSubscriber build(NettyHttpRequest request) {
        return new JsonContentSubscriber(request, httpServerConfiguration);
    }
}
