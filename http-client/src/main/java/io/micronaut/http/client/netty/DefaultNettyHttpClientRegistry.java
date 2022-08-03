/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.StreamingHttpClientRegistry;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.ProxyHttpClientRegistry;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.client.sse.SseClientRegistry;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.codec.MapperMediaTypeCodec;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.WebSocketClientRegistry;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

/**
 * Factory for the default implementation of the {@link HttpClient} interface based on Netty.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@Factory
@BootstrapContextCompatible
@Internal
public
class DefaultNettyHttpClientRegistry implements AutoCloseable,
        HttpClientRegistry<HttpClient>,
        SseClientRegistry<SseClient>,
        StreamingHttpClientRegistry<StreamingHttpClient>,
        WebSocketClientRegistry<WebSocketClient>,
        ProxyHttpClientRegistry<ProxyHttpClient>,
        ChannelPipelineCustomizer,
        NettyClientCustomizer.Registry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultNettyHttpClientRegistry.class);
    private final Map<ClientKey, DefaultHttpClient> clients = new ConcurrentHashMap<>(10);
    private final LoadBalancerResolver loadBalancerResolver;
    private final NettyClientSslBuilder nettyClientSslBuilder;
    private final ThreadFactory threadFactory;
    private final MediaTypeCodecRegistry codecRegistry;
    private final BeanContext beanContext;
    private final HttpClientConfiguration defaultHttpClientConfiguration;
    private final EventLoopGroupRegistry eventLoopGroupRegistry;
    private final List<InvocationInstrumenterFactory> invocationInstrumenterFactories;
    private final EventLoopGroupFactory eventLoopGroupFactory;
    private final HttpClientFilterResolver<ClientFilterResolutionContext> clientFilterResolver;
    private final JsonMapper jsonMapper;
    private final Collection<ChannelPipelineListener> pipelineListeners = new CopyOnWriteArrayList<>();
    private final CompositeNettyClientCustomizer clientCustomizer = new CompositeNettyClientCustomizer();

    /**
     * Default constructor.
     *
     * @param defaultHttpClientConfiguration  The default HTTP client configuration
     * @param httpClientFilterResolver        The HTTP client filter resolver
     * @param loadBalancerResolver            The load balancer resolver
     * @param nettyClientSslBuilder           The client SSL builder
     * @param threadFactory                   The thread factory
     * @param codecRegistry                   The codec registry
     * @param eventLoopGroupRegistry          The event loop group registry
     * @param eventLoopGroupFactory           The event loop group factory
     * @param beanContext                     The bean context
     * @param invocationInstrumenterFactories The invocation instrumenter factories
     * @param jsonMapper                      JSON Mapper
     */
    public DefaultNettyHttpClientRegistry(
            HttpClientConfiguration defaultHttpClientConfiguration,
            HttpClientFilterResolver httpClientFilterResolver,
            LoadBalancerResolver loadBalancerResolver,
            NettyClientSslBuilder nettyClientSslBuilder,
            ThreadFactory threadFactory,
            MediaTypeCodecRegistry codecRegistry,
            EventLoopGroupRegistry eventLoopGroupRegistry,
            EventLoopGroupFactory eventLoopGroupFactory,
            BeanContext beanContext,
            List<InvocationInstrumenterFactory> invocationInstrumenterFactories,
            JsonMapper jsonMapper) {
        this.clientFilterResolver = httpClientFilterResolver;
        this.defaultHttpClientConfiguration = defaultHttpClientConfiguration;
        this.loadBalancerResolver = loadBalancerResolver;
        this.nettyClientSslBuilder = nettyClientSslBuilder;
        this.threadFactory = threadFactory;
        this.codecRegistry = codecRegistry;
        this.beanContext = beanContext;
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.eventLoopGroupRegistry = eventLoopGroupRegistry;
        this.invocationInstrumenterFactories = invocationInstrumenterFactories;
        this.jsonMapper = jsonMapper;
    }

    @NonNull
    @Override
    public HttpClient getClient(HttpVersion httpVersion, @NonNull String clientId, @Nullable String path) {
        final ClientKey key = new ClientKey(
                httpVersion,
                clientId,
                null,
                path,
                null,
                null
        );
        return getClient(key, beanContext, AnnotationMetadata.EMPTY_METADATA);
    }

    @Override
    @NonNull
    public DefaultHttpClient getClient(@NonNull AnnotationMetadata metadata) {
        final ClientKey key = getClientKey(metadata);
        return getClient(key, beanContext, metadata);
    }

    @Override
    @NonNull
    public DefaultHttpClient getSseClient(@NonNull AnnotationMetadata metadata) {
        return getClient(metadata);
    }

    @Override
    @NonNull
    public DefaultHttpClient getStreamingHttpClient(@NonNull AnnotationMetadata metadata) {
        return getClient(metadata);
    }

    @Override
    @NonNull
    public DefaultHttpClient getProxyHttpClient(@NonNull AnnotationMetadata metadata) {
        return getClient(metadata);
    }

    @Override
    @NonNull
    public DefaultHttpClient getWebSocketClient(@NonNull AnnotationMetadata metadata) {
        return getClient(metadata);
    }

    @Override
    @PreDestroy
    public void close() {
        for (HttpClient httpClient : clients.values()) {
            try {
                httpClient.close();
            } catch (Throwable e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down HTTP client: " + e.getMessage(), e);
                }
            }
        }
        clients.clear();
    }

    @Override
    public void disposeClient(AnnotationMetadata annotationMetadata) {
        final ClientKey key = getClientKey(annotationMetadata);
        final StreamingHttpClient streamingHttpClient = clients.get(key);
        if (streamingHttpClient != null && streamingHttpClient.isRunning()) {
            streamingHttpClient.close();
            clients.remove(key);
        }
    }

    /**
     * Creates a new {@link HttpClient} for the given injection point.
     *
     * @param injectionPoint The injection point
     * @param loadBalancer   The load balancer to use (Optional)
     * @param configuration  The configuration (Optional)
     * @param beanContext    The bean context to use
     * @return The client
     */
    @Bean
    @BootstrapContextCompatible
    @Primary
    protected DefaultHttpClient httpClient(
            @Nullable InjectionPoint<?> injectionPoint,
            @Parameter @Nullable LoadBalancer loadBalancer,
            @Parameter @Nullable HttpClientConfiguration configuration,
            BeanContext beanContext) {
        return resolveDefaultHttpClient(injectionPoint, loadBalancer, configuration, beanContext);
    }

    @Override
    @NonNull
    public HttpClient resolveClient(@Nullable InjectionPoint<?>  injectionPoint,
                                    @Nullable LoadBalancer loadBalancer,
                                    @Nullable HttpClientConfiguration configuration,
                                    @NonNull BeanContext beanContext) {
        return resolveDefaultHttpClient(injectionPoint, loadBalancer, configuration, beanContext);
    }

    @Override
    @NonNull
    public ProxyHttpClient resolveProxyHttpClient(@Nullable InjectionPoint<?>  injectionPoint,
                                                  @Nullable LoadBalancer loadBalancer,
                                                  @Nullable HttpClientConfiguration configuration,
                                                  @NonNull BeanContext beanContext) {
        return resolveDefaultHttpClient(injectionPoint, loadBalancer, configuration, beanContext);
    }

    @Override
    @NonNull
    public SseClient resolveSseClient(@Nullable InjectionPoint<?>  injectionPoint,
                                      @Nullable LoadBalancer loadBalancer,
                                      @Nullable HttpClientConfiguration configuration,
                                      @NonNull BeanContext beanContext) {
        return resolveDefaultHttpClient(injectionPoint, loadBalancer, configuration, beanContext);
    }

    @Override
    @NonNull
    public StreamingHttpClient resolveStreamingHttpClient(@Nullable InjectionPoint<?>  injectionPoint,
                                                          @Nullable LoadBalancer loadBalancer,
                                                          @Nullable HttpClientConfiguration configuration,
                                                          @NonNull BeanContext beanContext) {
        return resolveDefaultHttpClient(injectionPoint, loadBalancer, configuration, beanContext);
    }

    @Override
    @NonNull
    public WebSocketClient resolveWebSocketClient(@Nullable InjectionPoint<?> injectionPoint,
                                                  @Nullable LoadBalancer loadBalancer,
                                                  @Nullable HttpClientConfiguration configuration,
                                                  @NonNull BeanContext beanContext) {
        return resolveDefaultHttpClient(injectionPoint, loadBalancer, configuration, beanContext);
    }

    @Override
    public boolean isClientChannel() {
        return true;
    }

    @Override
    public void doOnConnect(@NonNull ChannelPipelineListener listener) {
        Objects.requireNonNull(listener, "listener");
        pipelineListeners.add(listener);
    }

    @Override
    public void register(@NonNull NettyClientCustomizer customizer) {
        Objects.requireNonNull(customizer, "customizer");
        clientCustomizer.add(customizer);
    }

    private DefaultHttpClient getClient(ClientKey key, BeanContext beanContext, AnnotationMetadata annotationMetadata) {
        return clients.computeIfAbsent(key, clientKey -> {
            DefaultHttpClient clientBean = null;
            final String clientId = clientKey.clientId;
            final Class<?> configurationClass = clientKey.configurationClass;

            if (clientId != null) {
                clientBean = (DefaultHttpClient) this.beanContext
                        .findBean(HttpClient.class, Qualifiers.byName(clientId)).orElse(null);
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


            final DefaultHttpClient client = buildClient(
                    loadBalancer,
                    clientKey.httpVersion,
                    configuration,
                    clientId,
                    contextPath,
                    beanContext,
                    annotationMetadata
            );
            final JsonFeatures jsonFeatures = clientKey.jsonFeatures;
            if (jsonFeatures != null) {
                List<MediaTypeCodec> codecs = new ArrayList<>(2);
                MediaTypeCodecRegistry codecRegistry = client.getMediaTypeCodecRegistry();
                for (MediaTypeCodec codec : codecRegistry.getCodecs()) {
                    if (codec instanceof MapperMediaTypeCodec) {
                        codecs.add(((MapperMediaTypeCodec) codec).cloneWithFeatures(jsonFeatures));
                    } else {
                        codecs.add(codec);
                    }
                }
                if (!codecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE).isPresent()) {
                    codecs.add(createNewJsonCodec(this.beanContext, jsonFeatures));
                }
                client.setMediaTypeCodecRegistry(MediaTypeCodecRegistry.of(codecs));
            }
            return client;
        });
    }

    private DefaultHttpClient buildClient(
            LoadBalancer loadBalancer,
            HttpVersion httpVersion,
            HttpClientConfiguration configuration,
            String clientId,
            String contextPath,
            BeanContext beanContext,
            AnnotationMetadata annotationMetadata) {

        EventLoopGroup eventLoopGroup = resolveEventLoopGroup(configuration, beanContext);
        return new DefaultHttpClient(
                loadBalancer,
                httpVersion,
                configuration,
                contextPath,
                clientFilterResolver,
                clientFilterResolver.resolveFilterEntries(new ClientFilterResolutionContext(
                        clientId == null ? null : Collections.singletonList(clientId),
                        annotationMetadata
                )),
                threadFactory,
                nettyClientSslBuilder,
                codecRegistry,
                WebSocketBeanRegistry.forClient(beanContext),
                beanContext.findBean(RequestBinderRegistry.class).orElseGet(() ->
                        new DefaultRequestBinderRegistry(ConversionService.SHARED)
                ),
                eventLoopGroup,
                resolveSocketChannelFactory(configuration, beanContext),
                pipelineListeners,
                clientCustomizer,
                invocationInstrumenterFactories,
                clientId
        );
    }

    private EventLoopGroup resolveEventLoopGroup(HttpClientConfiguration configuration, BeanContext beanContext) {
        final String eventLoopGroupName = configuration.getEventLoopGroup();
        EventLoopGroup eventLoopGroup;
        if (EventLoopGroupConfiguration.DEFAULT.equals(eventLoopGroupName)) {
            eventLoopGroup = eventLoopGroupRegistry.getDefaultEventLoopGroup();
        } else {
            eventLoopGroup = beanContext.findBean(EventLoopGroup.class, Qualifiers.byName(eventLoopGroupName))
                    .orElseThrow(() -> new HttpClientException("Specified event loop group is not defined: " + eventLoopGroupName));
        }
        return eventLoopGroup;
    }

    private DefaultHttpClient resolveDefaultHttpClient(
            @Nullable InjectionPoint injectionPoint,
            @Nullable LoadBalancer loadBalancer,
            @Nullable HttpClientConfiguration configuration,
            @NonNull BeanContext beanContext) {
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
                    beanContext,
                    AnnotationMetadata.EMPTY_METADATA
            );
        } else {
            return getClient(injectionPoint != null ? injectionPoint.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);
        }
    }

    private ChannelFactory resolveSocketChannelFactory(HttpClientConfiguration configuration, BeanContext beanContext) {
        final String eventLoopGroup = configuration.getEventLoopGroup();

        final EventLoopGroupConfiguration eventLoopGroupConfiguration = beanContext.findBean(EventLoopGroupConfiguration.class, Qualifiers.byName(eventLoopGroup))
                .orElseGet(() -> {
                    if (EventLoopGroupConfiguration.DEFAULT.equals(eventLoopGroup)) {
                        return new DefaultEventLoopGroupConfiguration();
                    } else {
                        throw new HttpClientException("Specified event loop group is not defined: " + eventLoopGroup);
                    }
                });

        return () -> eventLoopGroupFactory.clientSocketChannelInstance(eventLoopGroupConfiguration);
    }

    private ClientKey getClientKey(AnnotationMetadata metadata) {
        final HttpVersion httpVersion =
                metadata.enumValue(Client.class, "httpVersion", HttpVersion.class).orElse(null);
        String clientId = metadata.stringValue(Client.class).orElse(null);
        String path = metadata.stringValue(Client.class, "path").orElse(null);
        List<String> filterAnnotation = metadata
                .getAnnotationNamesByStereotype(FilterMatcher.class);
        final Class configurationClass =
                metadata.classValue(Client.class, "configuration").orElse(null);
        JsonFeatures jsonFeatures = jsonMapper.detectFeatures(metadata).orElse(null);

        return new ClientKey(httpVersion, clientId, filterAnnotation, path, configurationClass, jsonFeatures);
    }

    private static MediaTypeCodec createNewJsonCodec(BeanContext beanContext, JsonFeatures jsonFeatures) {
        return getJsonCodec(beanContext).cloneWithFeatures(jsonFeatures);
    }

    private static MapperMediaTypeCodec getJsonCodec(BeanContext beanContext) {
        return beanContext.getBean(MapperMediaTypeCodec.class, Qualifiers.byName(MapperMediaTypeCodec.REGULAR_JSON_MEDIA_TYPE_CODEC_NAME));
    }

    /**
     * Client key.
     */
    @Internal
    private static final class ClientKey {
        final HttpVersion httpVersion;
        final String clientId;
        final List<String> filterAnnotations;
        final String path;
        final Class<?> configurationClass;
        final JsonFeatures jsonFeatures;

        ClientKey(
                HttpVersion httpVersion,
                String clientId,
                List<String> filterAnnotations,
                String path,
                Class<?> configurationClass,
                JsonFeatures jsonFeatures) {
            this.httpVersion = httpVersion;
            this.clientId = clientId;
            this.filterAnnotations = filterAnnotations;
            this.path = path;
            this.configurationClass = configurationClass;
            this.jsonFeatures = jsonFeatures;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClientKey clientKey = (ClientKey) o;
            return httpVersion == clientKey.httpVersion &&
                    Objects.equals(clientId, clientKey.clientId) &&
                    Objects.equals(filterAnnotations, clientKey.filterAnnotations) &&
                    Objects.equals(path, clientKey.path) &&
                    Objects.equals(configurationClass, clientKey.configurationClass) &&
                    Objects.equals(jsonFeatures, clientKey.jsonFeatures);
        }

        @Override
        public int hashCode() {
            return Objects.hash(httpVersion, clientId, filterAnnotations, path, configurationClass, jsonFeatures);
        }
    }
}
