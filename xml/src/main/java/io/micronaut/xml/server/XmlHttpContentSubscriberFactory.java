/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.xml.server;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentSubscriberFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;

import javax.inject.Singleton;

/**
 * Builds the {@link org.reactivestreams.Subscriber} for XML requests.
 *
 * @author svishnyakov
 * @since 1.3.0
 */
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_XHTML, MediaType.TEXT_XML})
@Singleton
public class XmlHttpContentSubscriberFactory implements HttpContentSubscriberFactory {

    private final NettyHttpServerConfiguration configuration;

    /**
     * @param configuration The {@link NettyHttpServerConfiguration}
     */
    public XmlHttpContentSubscriberFactory(NettyHttpServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public HttpContentProcessor build(NettyHttpRequest request) {
        return new XmlContentProcessor(request, configuration);
    }
}
