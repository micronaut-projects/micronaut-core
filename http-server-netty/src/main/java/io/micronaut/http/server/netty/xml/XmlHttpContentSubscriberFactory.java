package io.micronaut.http.server.netty.xml;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentSubscriberFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;

import javax.inject.Singleton;

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