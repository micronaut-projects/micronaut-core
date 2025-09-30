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
import io.micronaut.context.BeanProvider;
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
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyHttpClientRegistry;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.StreamingHttpClientRegistry;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.netty.ssl.ClientSslBuilder;
import io.micronaut.http.client.netty.ssl.NettyClientSslFactory;
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
import io.micronaut.http.netty.channel.NettyChannelType;
import io.micronaut.http.ssl.CertificateProvider;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.CustomizableJsonHandler;
import io.micronaut.json.codec.MapperMediaTypeCodec;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.runtime.context.scope.refresh.RefreshEventListener;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.WebSocketClientRegistry;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolverGroup;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
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
        RawHttpClientRegistry,
        ChannelPipelineCustomizer,
        NettyClientCustomizer.Registry,
        RefreshEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultNettyHttpClientRegistry.class);
    private final Map<ClientKey, DefaultHttpClient> unbalancedClients = new ConcurrentHashMap<>(10);
    private final List<DefaultHttpClient> balancedClients = Collections.synchronizedList(new ArrayList<>());
    private final LoadBalancerResolver loadBalancerResolver;
    private final ClientSslBuilder nettyClientSslBuilder;
    private final NettyClientSslFactory sslFactory;
    private final BeanProvider<CertificateProvider> certificateProviders;
    private final ThreadFactory threadFactory;
    private final MediaTypeCodecRegistry codecRegistry;
    private final MessageBodyHandlerRegistry handlerRegistry;
    private final BeanContext beanContext;
    private final HttpClientConfiguration defaultHttpClientConfiguration;
    private final EventLoopGroupRegistry eventLoopGroupRegistry;
    private final EventLoopGroupFactory eventLoopGroupFactory;
    private final HttpClientFilterResolver<ClientFilterResolutionContext> clientFilterResolver;
    private final JsonMapper jsonMapper;
    private final Collection<ChannelPipelineListener> pipelineListeners = new CopyOnWriteArrayList<>();
    private final CompositeNettyClientCustomizer clientCustomizer = new CompositeNettyClientCustomizer();
    private final ExecutorService blockingExecutor;

    /**
     * Default constructor.
     *
     * @param defaultHttpClientConfiguration  The default HTTP client configuration
     * @param httpClientFilterResolver        The HTTP client filter resolver
     * @param loadBalancerResolver            The load balancer resolver
     * @param nettyClientSslBuilder           The client SSL builder
     * @param sslFactory                      The client SSL builder factory
     * @param certificateProviders            Certificate provider bean for named lookup
     * @param threadFactory                   The thread factory
     * @param codecRegistry                   The codec registry
     * @param handlerRegistry                 The handler registry
     * @param eventLoopGroupRegistry          The event loop group registry
     * @param eventLoopGroupFactory           The event loop group factory
     * @param beanContext                     The bean context
     * @param jsonMapper                      JSON Mapper
     * @param blockingExecutor                Optional executor for blocking operations
     */
    public DefaultNettyHttpClientRegistry(
        HttpClientConfiguration defaultHttpClientConfiguration,
        HttpClientFilterResolver<ClientFilterResolutionContext> httpClientFilterResolver,
        LoadBalancerResolver loadBalancerResolver,
        ClientSslBuilder nettyClientSslBuilder,
        NettyClientSslFactory sslFactory,
        BeanProvider<CertificateProvider> certificateProviders,
        ThreadFactory threadFactory,
        MediaTypeCodecRegistry codecRegistry,
        MessageBodyHandlerRegistry handlerRegistry,
        EventLoopGroupRegistry eventLoopGroupRegistry,
        EventLoopGroupFactory eventLoopGroupFactory,
        BeanContext beanContext,
        JsonMapper jsonMapper,
        @Nullable @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor) {
        this.clientFilterResolver = httpClientFilterResolver;
        this.defaultHttpClientConfiguration = defaultHttpClientConfiguration;
        this.loadBalancerResolver = loadBalancerResolver;
        this.nettyClientSslBuilder = nettyClientSslBuilder;
        this.sslFactory = sslFactory;
        this.certificateProviders = certificateProviders;
        this.threadFactory = threadFactory;
        this.codecRegistry = codecRegistry;
        this.handlerRegistry = handlerRegistry;
        this.beanContext = beanContext;
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.eventLoopGroupRegistry = eventLoopGroupRegistry;
        this.jsonMapper = jsonMapper;
        this.blockingExecutor = blockingExecutor;
    }

    @NonNull
    @Override
    public DefaultHttpClient getClient(@NonNull HttpVersionSelection httpVersion, @NonNull String clientId, @Nullable String path) {
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
    public @NonNull RawHttpClient getRawClient(@NonNull HttpVersionSelection httpVersion, @NonNull String clientId, @Nullable String path) {
        return getClient(httpVersion, clientId, path);
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
        for (HttpClient httpClient : unbalancedClients.values()) {
            try {
                httpClient.close();
            } catch (Throwable e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down HTTP client: {}", e.getMessage(), e);
                }
            }
        }
        unbalancedClients.clear();
    }

    @Override
    public void disposeClient(AnnotationMetadata annotationMetadata) {
        final ClientKey key = getClientKey(annotationMetadata);
        final StreamingHttpClient streamingHttpClient = unbalancedClients.get(key);
        if (streamingHttpClient != null && streamingHttpClient.isRunning()) {
            streamingHttpClient.close();
            unbalancedClients.remove(key);
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
        return unbalancedClients.computeIfAbsent(key, clientKey -> {
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


            final DefaultHttpClientBuilder builder = clientBuilder(
                    configuration,
                    clientId,
                    beanContext,
                    annotationMetadata
            )
                .loadBalancer(loadBalancer)
                .explicitHttpVersion(clientKey.httpVersion)
                .contextPath(contextPath);
            final JsonFeatures jsonFeatures = clientKey.jsonFeatures;
            if (jsonFeatures != null) {
                List<MediaTypeCodec> codecs = new ArrayList<>(2);
                MediaTypeCodecRegistry codecRegistry = builder.codecRegistry;
                for (MediaTypeCodec codec : codecRegistry.getCodecs()) {
                    if (codec instanceof MapperMediaTypeCodec typeCodec) {
                        codecs.add(typeCodec.cloneWithFeatures(jsonFeatures));
                    } else {
                        codecs.add(codec);
                    }
                }
                if (!codecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE).isPresent()) {
                    codecs.add(createNewJsonCodec(this.beanContext, jsonFeatures));
                }
                builder.codecRegistry(MediaTypeCodecRegistry.of(codecs));
                builder.handlerRegistry(new MessageBodyHandlerRegistry() {
                    final MessageBodyHandlerRegistry delegate = builder.handlerRegistry;

                    @SuppressWarnings("unchecked")
                    private <T> T customize(T handler) {
                        if (handler instanceof CustomizableJsonHandler cnjh) {
                            return (T) cnjh.customize(jsonFeatures);
                        }
                        return handler;
                    }

                    @Override
                    public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaType) {
                        return delegate.findReader(type, mediaType).map(this::customize);
                    }

                    @Override
                    public <T> Optional<MessageBodyWriter<T>> findWriter(Argument<T> type, List<MediaType> mediaType) {
                        return delegate.findWriter(type, mediaType).map(this::customize);
                    }
                });
            }
            return builder.build();
        });
    }

    private DefaultHttpClientBuilder clientBuilder(
            HttpClientConfiguration configuration,
            String clientId,
            BeanContext beanContext,
            AnnotationMetadata annotationMetadata) {

        String addressResolverGroupName = configuration.getAddressResolverGroupName();
        DefaultHttpClientBuilder builder = DefaultHttpClient.builder();
        beanContext.findBean(RequestBinderRegistry.class).ifPresent(builder::requestBinderRegistry);
        return builder
            .configuration(configuration)
            .filterResolver(clientFilterResolver)
            .clientFilterEntries(clientFilterResolver.resolveFilterEntries(new ClientFilterResolutionContext(
                clientId == null ? null : Collections.singletonList(clientId),
                annotationMetadata
            )))
            .threadFactory(threadFactory)
            .nettyClientSslBuilder(nettyClientSslBuilder)
            .sslFactory(sslFactory, certificateProviders)
            .codecRegistry(codecRegistry)
            .handlerRegistry(handlerRegistry)
            .webSocketBeanRegistry(WebSocketBeanRegistry.forClient(beanContext))
            .eventLoopGroup(resolveEventLoopGroup(configuration, beanContext))
            .socketChannelFactory(resolveSocketChannelFactory(NettyChannelType.CLIENT_SOCKET, configuration, beanContext))
            .udpChannelFactory(resolveSocketChannelFactory(NettyChannelType.DATAGRAM_SOCKET, configuration, beanContext))
            .clientCustomizer(clientCustomizer)
            .informationalServiceId(clientId)
            .conversionService(beanContext.getBean(ConversionService.class))
            .resolverGroup(addressResolverGroupName == null ? null : beanContext.getBean(AddressResolverGroup.class, Qualifiers.byName(addressResolverGroupName)))
            .blockingExecutor(blockingExecutor);
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
            DefaultHttpClient c = clientBuilder(
                configuration,
                null,
                beanContext,
                AnnotationMetadata.EMPTY_METADATA
            )
                .loadBalancer(loadBalancer)
                .contextPath(loadBalancer.getContextPath().orElse(null))
                .build();
            balancedClients.add(c);
            return c;
        } else {
            return getClient(injectionPoint != null ? injectionPoint.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);
        }
    }

    private ChannelFactory<? extends Channel> resolveSocketChannelFactory(NettyChannelType type, HttpClientConfiguration configuration, BeanContext beanContext) {
        final String eventLoopGroup = configuration.getEventLoopGroup();

        final EventLoopGroupConfiguration eventLoopGroupConfiguration = beanContext.findBean(EventLoopGroupConfiguration.class, Qualifiers.byName(eventLoopGroup))
                .orElseGet(() -> {
                    if (EventLoopGroupConfiguration.DEFAULT.equals(eventLoopGroup)) {
                        return new DefaultEventLoopGroupConfiguration();
                    } else {
                        throw new HttpClientException("Specified event loop group is not defined: " + eventLoopGroup);
                    }
                });

        return () -> eventLoopGroupFactory.channelInstance(type, eventLoopGroupConfiguration);
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

    private static MediaTypeCodec createNewJsonCodec(BeanContext beanContext, JsonFeatures jsonFeatures) {
        return getJsonCodec(beanContext).cloneWithFeatures(jsonFeatures);
    }

    private static MapperMediaTypeCodec getJsonCodec(BeanContext beanContext) {
        return beanContext.getBean(MapperMediaTypeCodec.class, Qualifiers.byName(MapperMediaTypeCodec.REGULAR_JSON_MEDIA_TYPE_CODEC_NAME));
    }

    @Override
    public Set<String> getObservedConfigurationPrefixes() {
        return Set.of(DefaultHttpClientConfiguration.PREFIX, ServiceHttpClientConfiguration.PREFIX, SslConfiguration.PREFIX);
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        for (DefaultHttpClient client : unbalancedClients.values()) {
            client.connectionManager.refresh();
        }
        for (DefaultHttpClient client : balancedClients) {
            client.connectionManager.refresh();
        }
    }

    /**
     * Client key.
     */
    @Internal
    private static final class ClientKey {
        final HttpVersionSelection httpVersion;
        final String clientId;
        final List<String> filterAnnotations;
        final String path;
        final Class<?> configurationClass;
        final JsonFeatures jsonFeatures;

        ClientKey(
            HttpVersionSelection httpVersion,
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
            return Objects.equals(httpVersion, clientKey.httpVersion) &&
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
