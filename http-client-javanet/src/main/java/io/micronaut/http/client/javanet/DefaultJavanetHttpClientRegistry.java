package io.micronaut.http.client.javanet;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.codec.MapperMediaTypeCodec;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Factory
@BootstrapContextCompatible
@Internal
public
class DefaultJavanetHttpClientRegistry implements AutoCloseable,
    HttpClientRegistry<HttpClient> {

    private final BeanContext beanContext;
    public DefaultJavanetHttpClientRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public HttpClient getClient(AnnotationMetadata annotationMetadata) {
        throw new UnsupportedOperationException("unimplemented");
    }

    @Override
    public HttpClient getClient(HttpVersionSelection httpVersion, String clientId, String path) {
        throw new UnsupportedOperationException("unimplemented");
    }

    @Override
    public HttpClient resolveClient(InjectionPoint<?> injectionPoint, LoadBalancer loadBalancer, HttpClientConfiguration configuration, BeanContext beanContext) {
        throw new UnsupportedOperationException("unimplemented");
    }

    @Override
    public void disposeClient(AnnotationMetadata annotationMetadata) {
        //TODO
    }

    @Override
    public void close() throws Exception {
        //TODO
    }

}
