/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.jdk;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.codec.MapperMediaTypeCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory to create {@literal java.net.http.*} HTTP Clients.
 */
@Factory
@BootstrapContextCompatible
@Internal
@Experimental
public class DefaultJdkHttpClientRegistry implements AutoCloseable, HttpClientRegistry<HttpClient> {

    private final Map<ClientKey, HttpClient> clients = new ConcurrentHashMap<>(10);
    private final BeanContext beanContext;
    private final LoadBalancerResolver loadBalancerResolver;
    private final HttpClientConfiguration defaultHttpClientConfiguration;
    private final JsonMapper jsonMapper;
    @Nullable
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;

    public DefaultJdkHttpClientRegistry(
        BeanContext beanContext,
        LoadBalancerResolver loadBalancerResolver, HttpClientConfiguration defaultHttpClientConfiguration,
        JsonMapper jsonMapper,
        @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry
    ) {
        this.beanContext = beanContext;
        this.loadBalancerResolver = loadBalancerResolver;
        this.defaultHttpClientConfiguration = defaultHttpClientConfiguration;
        this.jsonMapper = jsonMapper;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    /**
     * Creates a {@literal java.net.http.*} HTTP Client.
     *
     * @param injectionPoint
     * @param loadBalancer
     * @param configuration
     * @param beanContext
     * @return A {@literal java.net.http.*} HTTP Client
     */
    @Bean
    @BootstrapContextCompatible
    @Primary
    protected HttpClient httpClient(
        @Nullable InjectionPoint<?> injectionPoint,
        @Parameter @Nullable LoadBalancer loadBalancer,
        @Parameter @Nullable HttpClientConfiguration configuration,
        BeanContext beanContext
    ) {
        return resolveDefaultHttpClient(injectionPoint, loadBalancer, configuration, beanContext);
    }

    private HttpClient resolveDefaultHttpClient(
        @Nullable InjectionPoint<?> injectionPoint,
        @Nullable LoadBalancer loadBalancer,
        @Nullable HttpClientConfiguration configuration,
        BeanContext beanContext
        ) {
        if (loadBalancer != null) {
            if (configuration == null) {
                configuration = defaultHttpClientConfiguration;
            }
            return buildClient(
                loadBalancer,
                null,
                configuration,
                null,
                loadBalancer.getContextPath().orElse(null),
                beanContext
            );
        } else {
            return getClient(injectionPoint != null ? injectionPoint.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);
        }
    }

    @Override
    public HttpClient getClient(AnnotationMetadata annotationMetadata) {
        final ClientKey key = getClientKey(annotationMetadata);
        return getClient(key);
    }

    private ClientKey getClientKey(AnnotationMetadata metadata) {
        HttpVersionSelection httpVersionSelection = HttpVersionSelection.forClientAnnotation(metadata);
        String clientId = metadata.stringValue(Client.class).orElse(null);
        String path = metadata.stringValue(Client.class, "path").orElse(null);
        List<String> filterAnnotation = metadata
            .getAnnotationNamesByStereotype(FilterMatcher.class);
        final Class<?> configurationClass =
            metadata.classValue(Client.class, "configuration").orElse(null);
        JsonFeatures jsonFeatures = jsonMapper.detectFeatures(metadata).orElse(null);

        return new ClientKey(httpVersionSelection, clientId, filterAnnotation, path, configurationClass, jsonFeatures);
    }

    private HttpClient getClient(ClientKey key) {
        return clients.computeIfAbsent(key, clientKey -> {
            HttpClient clientBean = null;
            final String clientId = clientKey.clientId;
            final Class<?> configurationClass = clientKey.configurationClass;

            if (clientId != null) {
                clientBean = this.beanContext.findBean(HttpClient.class, Qualifiers.byName(clientId)).orElse(null);
            }

            if (configurationClass != null && !HttpClientConfiguration.class.isAssignableFrom(configurationClass)) {
                throw new IllegalStateException("Referenced HTTP client configuration class must be an instance of HttpClientConfiguration for injection point: " + configurationClass);
            }

            final List<String> filterAnnotations = clientKey.filterAnnotations;
            final String path = clientKey.path;
            if (clientBean != null && path == null && configurationClass == null && filterAnnotations.isEmpty()) {
                return clientBean;
            }

            LoadBalancer loadBalancer = null;
            final HttpClientConfiguration configuration;
            if (configurationClass != null) {
                configuration = (HttpClientConfiguration) this.beanContext.getBean(configurationClass);
            } else if (clientId != null) {
                configuration = this.beanContext.findBean(
                    HttpClientConfiguration.class,
                    Qualifiers.byName(clientId)
                ).orElse(defaultHttpClientConfiguration);
            } else {
                configuration = defaultHttpClientConfiguration;
            }

            if (clientId != null) {
                loadBalancer = loadBalancerResolver.resolve(clientId)
                    .orElseThrow(() ->
                        new HttpClientException("Invalid service reference [" + clientId + "] specified to @Client"));
            }

            String contextPath = null;
            if (StringUtils.isNotEmpty(path)) {
                contextPath = path;
            } else if (StringUtils.isNotEmpty(clientId) && clientId.startsWith("/")) {
                contextPath = clientId;
            } else {
                if (loadBalancer != null) {
                    contextPath = loadBalancer.getContextPath().orElse(null);
                }
            }

            JdkHttpClient client = buildClient(loadBalancer, clientKey.httpVersion, configuration, clientId, contextPath, beanContext);

            final JsonFeatures jsonFeatures = clientKey.jsonFeatures;
            if (jsonFeatures != null) {
                List<MediaTypeCodec> codecs = new ArrayList<>(2);
                MediaTypeCodecRegistry codecRegistry = client.getMediaTypeCodecRegistry();
                for (MediaTypeCodec codec : codecRegistry.getCodecs()) {
                    if (codec instanceof MapperMediaTypeCodec mapper) {
                        codecs.add(mapper.cloneWithFeatures(jsonFeatures));
                    } else {
                        codecs.add(codec);
                    }
                }
                if (codecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE).isEmpty()) {
                    codecs.add(createNewJsonCodec(this.beanContext, jsonFeatures));
                }
                client.setMediaTypeCodecRegistry(MediaTypeCodecRegistry.of(codecs));
            }
            return client;
        });
    }

    private JdkHttpClient buildClient(
        LoadBalancer loadBalancer,
        HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        String clientId,
        String contextPath,
        BeanContext beanContext
    ) {
        ConversionService conversionService = beanContext.getBean(ConversionService.class);
        return new JdkHttpClient(
            loadBalancer,
            httpVersion,
            configuration,
            contextPath,
            mediaTypeCodecRegistry,
            beanContext.findBean(RequestBinderRegistry.class).orElseGet(() ->
                new DefaultRequestBinderRegistry(conversionService)
            ),
            clientId,
            conversionService,
            beanContext.findBean(JdkClientSslBuilder.class).orElseGet(() ->
                new JdkClientSslBuilder(new ResourceResolver())
            )
        );
    }

    private static MediaTypeCodec createNewJsonCodec(BeanContext beanContext, JsonFeatures jsonFeatures) {
        return getJsonCodec(beanContext).cloneWithFeatures(jsonFeatures);
    }

    private static MapperMediaTypeCodec getJsonCodec(BeanContext beanContext) {
        return beanContext.getBean(MapperMediaTypeCodec.class, Qualifiers.byName(MapperMediaTypeCodec.REGULAR_JSON_MEDIA_TYPE_CODEC_NAME));
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

    /**
     * Client key.
     */
    @Internal
    private record ClientKey (
        HttpVersionSelection httpVersion,
        String clientId,
        List<String> filterAnnotations,
        String path,
        Class<?> configurationClass,
        JsonFeatures jsonFeatures
    ) {}
}
