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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jackson.ObjectMapperFactory;
import io.micronaut.jackson.annotation.JacksonFeatures;
import io.micronaut.jackson.codec.JacksonMediaTypeCodec;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
public class RxNettyHttpClientRegistry implements AutoCloseable, RxHttpClientRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(RxNettyHttpClientRegistry.class);
    private final Map<ClientKey, DefaultHttpClient> clients = new ConcurrentHashMap<>(10);
    private final LoadBalancerResolver loadBalancerResolver;
    private final NettyClientSslBuilder nettyClientSslBuilder;
    private final ThreadFactory threadFactory;
    private final MediaTypeCodecRegistry codecRegistry;
    private final BeanContext beanContext;
    private final HttpClientConfiguration defaultHttpClientConfiguration;
    private final EventLoopGroupRegistry eventLoopGroupRegistry;
    private final EventLoopGroupFactory eventLoopGroupFactory;
    private final HttpClientFilterResolver<ClientFilterResolutionContext> clientFilterResolver;

    /**
     * Default constructor.
     *
     * @param defaultHttpClientConfiguration The default HTTP client configuration
     * @param httpClientFilterResolver       The HTTP client filter resolver
     * @param loadBalancerResolver           The load balancer resolver
     * @param nettyClientSslBuilder          The client SSL builder
     * @param threadFactory                  The thread factory
     * @param codecRegistry                  The codec registry
     * @param eventLoopGroupRegistry         The event loop group registry
     * @param eventLoopGroupFactory          The event loop group factory
     * @param beanContext                    The bean context
     */
    public RxNettyHttpClientRegistry(
            HttpClientConfiguration defaultHttpClientConfiguration,
            HttpClientFilterResolver httpClientFilterResolver,
            LoadBalancerResolver loadBalancerResolver,
            NettyClientSslBuilder nettyClientSslBuilder,
            ThreadFactory threadFactory,
            MediaTypeCodecRegistry codecRegistry,
            EventLoopGroupRegistry eventLoopGroupRegistry,
            EventLoopGroupFactory eventLoopGroupFactory,
            BeanContext beanContext) {
        this.clientFilterResolver = httpClientFilterResolver;
        this.defaultHttpClientConfiguration = defaultHttpClientConfiguration;
        this.loadBalancerResolver = loadBalancerResolver;
        this.nettyClientSslBuilder = nettyClientSslBuilder;
        this.threadFactory = threadFactory;
        this.codecRegistry = codecRegistry;
        this.beanContext = beanContext;
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.eventLoopGroupRegistry = eventLoopGroupRegistry;
    }

    @NonNull
    @Override
    public RxHttpClient getClient(HttpVersion httpVersion, @NonNull String clientId, @Nullable String path) {
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
    public DefaultHttpClient getClient(AnnotationMetadata metadata) {
        final ClientKey key = getClientKey(metadata);
        return getClient(key, beanContext, metadata);
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
        final RxStreamingHttpClient rxStreamingHttpClient = clients.get(key);
        if (rxStreamingHttpClient != null && rxStreamingHttpClient.isRunning()) {
            rxStreamingHttpClient.close();
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
        return resolveClient(injectionPoint, loadBalancer, configuration, beanContext);
    }

    private DefaultHttpClient getClient(ClientKey key, BeanContext beanContext, AnnotationMetadata annotationMetadata) {
        return clients.computeIfAbsent(key, clientKey -> {
            DefaultHttpClient clientBean = null;
            final String clientId = clientKey.clientId;
            final Class configurationClass = clientKey.configurationClass;
            final String filterAnnotation = clientKey.filterAnnotation;
            final String path = clientKey.path;

            if (clientId != null) {
                clientBean = (DefaultHttpClient) this.beanContext
                        .findBean(RxHttpClient.class, Qualifiers.byName(clientId)).orElse(null);
            }

            if (configurationClass != null && !HttpClientConfiguration.class.isAssignableFrom(configurationClass)) {
                throw new IllegalStateException("Referenced HTTP client configuration class must be an instance of HttpClientConfiguration for injection point: " + configurationClass);
            }


            if (clientBean != null && path == null && configurationClass == null && filterAnnotation == null) {
                return clientBean;
            }

            LoadBalancer loadBalancer = null;
            List<String> clientIdentifiers = null;
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
                clientIdentifiers = Collections.singletonList(clientId);
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
                    clientIdentifiers,
                    contextPath,
                    beanContext,
                    annotationMetadata
            );
            final AnnotationValue<JacksonFeatures> jacksonFeaturesAnn = clientKey.jacksonFeaturesAnn;
            if (jacksonFeaturesAnn != null) {
                io.micronaut.jackson.codec.JacksonFeatures jacksonFeatures = new io.micronaut.jackson.codec.JacksonFeatures();


                SerializationFeature[] enabledSerializationFeatures = jacksonFeaturesAnn.get("enabledSerializationFeatures", SerializationFeature[].class).orElse(null);
                if (enabledSerializationFeatures != null) {
                    for (SerializationFeature serializationFeature : enabledSerializationFeatures) {
                        jacksonFeatures.addFeature(serializationFeature, true);
                    }
                }

                DeserializationFeature[] enabledDeserializationFeatures = jacksonFeaturesAnn.get("enabledDeserializationFeatures", DeserializationFeature[].class).orElse(null);

                if (enabledDeserializationFeatures != null) {
                    for (DeserializationFeature deserializationFeature : enabledDeserializationFeatures) {
                        jacksonFeatures.addFeature(deserializationFeature, true);
                    }
                }

                SerializationFeature[] disabledSerializationFeatures = jacksonFeaturesAnn.get("disabledSerializationFeatures", SerializationFeature[].class).orElse(null);
                if (disabledSerializationFeatures != null) {
                    for (SerializationFeature serializationFeature : disabledSerializationFeatures) {
                        jacksonFeatures.addFeature(serializationFeature, false);
                    }
                }

                DeserializationFeature[] disabledDeserializationFeatures = jacksonFeaturesAnn.get("disabledDeserializationFeatures", DeserializationFeature[].class).orElse(null);

                if (disabledDeserializationFeatures != null) {
                    for (DeserializationFeature feature : disabledDeserializationFeatures) {
                        jacksonFeatures.addFeature(feature, false);
                    }
                }

                List<MediaTypeCodec> codecs = new ArrayList<>(2);
                MediaTypeCodecRegistry codecRegistry = client.getMediaTypeCodecRegistry();
                for (MediaTypeCodec codec : codecRegistry.getCodecs()) {
                    if (codec instanceof JacksonMediaTypeCodec) {
                        codecs.add(((JacksonMediaTypeCodec) codec).cloneWithFeatures(jacksonFeatures));
                    } else {
                        codecs.add(codec);
                    }
                }
                if (!codecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE).isPresent()) {
                    codecs.add(createNewJsonCodec(this.beanContext, jacksonFeatures));
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
            List<String> clientIdentifiers,
            String contextPath,
            BeanContext beanContext,
            AnnotationMetadata annotationMetadata) {

        EventLoopGroup eventLoopGroup = resolveEventLoopGroup(configuration, beanContext);
        Class<? extends SocketChannel> socketChannelClass = resolveSocketChannel(configuration, beanContext);
        return new DefaultHttpClient(
                loadBalancer,
                httpVersion,
                configuration,
                contextPath,
                clientFilterResolver,
                clientFilterResolver.resolveFilterEntries(new ClientFilterResolutionContext(
                    clientIdentifiers,
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
                socketChannelClass
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

    private DefaultHttpClient resolveClient(
            @Nullable InjectionPoint injectionPoint,
            @Nullable LoadBalancer loadBalancer,
            @Nullable HttpClientConfiguration configuration,
            BeanContext beanContext) {
        if (loadBalancer != null) {
            // direct creation via createBean
            List<HttpFilterResolver.FilterEntry<HttpClientFilter>> filterEntries = clientFilterResolver
                    .resolveFilterEntries(new ClientFilterResolutionContext(null, AnnotationMetadata.EMPTY_METADATA));
            EventLoopGroup eventLoopGroup = resolveEventLoopGroup(configuration, beanContext);
            Class<? extends SocketChannel> socketChannelClass = resolveSocketChannel(configuration, beanContext);
            return new DefaultHttpClient(
                    loadBalancer,
                    null,
                    configuration != null ? configuration : defaultHttpClientConfiguration,
                    loadBalancer.getContextPath().orElse(null),
                    clientFilterResolver,
                    filterEntries,
                    threadFactory,
                    nettyClientSslBuilder,
                    codecRegistry,
                    WebSocketBeanRegistry.forClient(beanContext),
                    beanContext.findBean(RequestBinderRegistry.class).orElseGet(() ->
                            new DefaultRequestBinderRegistry(ConversionService.SHARED)
                    ),
                    eventLoopGroup,
                    socketChannelClass
            );
        } else {
            return getClient(injectionPoint != null ? injectionPoint.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);
        }
    }

    private Class<? extends SocketChannel> resolveSocketChannel(HttpClientConfiguration configuration, BeanContext beanContext) {
        final String eventLoopGroup = configuration.getEventLoopGroup();

        final EventLoopGroupConfiguration eventLoopGroupConfiguration = beanContext.findBean(EventLoopGroupConfiguration.class, Qualifiers.byName(eventLoopGroup))
                .orElseGet(() -> {
                    if (EventLoopGroupConfiguration.DEFAULT.equals(eventLoopGroup)) {
                        return new DefaultEventLoopGroupConfiguration();
                    } else {
                        throw new HttpClientException("Specified event loop group is not defined: " + eventLoopGroup);
                    }
                });

        return eventLoopGroupFactory.clientSocketChannelClass(eventLoopGroupConfiguration);
    }

    private ClientKey getClientKey(AnnotationMetadata metadata) {
        final HttpVersion httpVersion =
                metadata.enumValue(Client.class, "httpVersion", HttpVersion.class).orElse(null);
        String clientId = metadata.stringValue(Client.class).orElse(null);
        String path = metadata.stringValue(Client.class, "path").orElse(null);
        String filterAnnotation = metadata
                .getAnnotationNameByStereotype(FilterMatcher.class).orElse(null);
        final Class configurationClass =
                metadata.classValue(Client.class, "configuration").orElse(null);
        AnnotationValue<JacksonFeatures> jacksonFeaturesAnn = metadata.findAnnotation(JacksonFeatures.class).orElse(null);

        return new ClientKey(httpVersion, clientId, filterAnnotation, path, configurationClass, jacksonFeaturesAnn);
    }

    private static MediaTypeCodec createNewJsonCodec(BeanContext beanContext, io.micronaut.jackson.codec.JacksonFeatures jacksonFeatures) {
        ObjectMapper objectMapper = new ObjectMapperFactory().objectMapper(null, null);

        jacksonFeatures.getDeserializationFeatures().forEach(objectMapper::configure);
        jacksonFeatures.getSerializationFeatures().forEach(objectMapper::configure);

        return new JsonMediaTypeCodec(objectMapper,
                beanContext.getBean(ApplicationConfiguration.class),
                beanContext.findBean(CodecConfiguration.class, Qualifiers.byName(JsonMediaTypeCodec.CONFIGURATION_QUALIFIER)).orElse(null));
    }

    /**
     * Client key.
     */
    @Internal
    private static final class ClientKey {
        final HttpVersion httpVersion;
        final String clientId;
        final String filterAnnotation;
        final String path;
        final Class<?> configurationClass;
        final AnnotationValue<JacksonFeatures> jacksonFeaturesAnn;

        ClientKey(
                HttpVersion httpVersion,
                String clientId,
                String filterAnnotation,
                String path,
                Class<?> configurationClass,
                AnnotationValue<JacksonFeatures> jacksonFeaturesAnn) {
            this.httpVersion = httpVersion;
            this.clientId = clientId;
            this.filterAnnotation = filterAnnotation;
            this.path = path;
            this.configurationClass = configurationClass;
            this.jacksonFeaturesAnn = jacksonFeaturesAnn;
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
                    Objects.equals(filterAnnotation, clientKey.filterAnnotation) &&
                    Objects.equals(path, clientKey.path) &&
                    Objects.equals(configurationClass, clientKey.configurationClass) &&
                    Objects.equals(jacksonFeaturesAnn, clientKey.jacksonFeaturesAnn);
        }

        @Override
        public int hashCode() {
            return Objects.hash(httpVersion, clientId, filterAnnotation, path, configurationClass, jacksonFeaturesAnn);
        }
    }
}
