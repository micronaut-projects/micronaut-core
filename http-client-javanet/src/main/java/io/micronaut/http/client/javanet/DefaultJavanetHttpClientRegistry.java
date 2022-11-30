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
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Factory
@BootstrapContextCompatible
@Internal
public
class DefaultJavanetHttpClientRegistry implements AutoCloseable,
    HttpClientRegistry<HttpClient> {

    private final Map<ClientKey, HttpClient> clients = new ConcurrentHashMap<>(10);
    private final BeanContext beanContext;
    private final LoadBalancerResolver loadBalancerResolver;
    private final HttpClientConfiguration defaultHttpClientConfiguration;
    private final JsonMapper jsonMapper;

    public DefaultJavanetHttpClientRegistry(
        BeanContext beanContext,
        LoadBalancerResolver loadBalancerResolver, HttpClientConfiguration defaultHttpClientConfiguration,
        JsonMapper jsonMapper) {
        this.beanContext = beanContext;
        this.loadBalancerResolver = loadBalancerResolver;
        this.defaultHttpClientConfiguration = defaultHttpClientConfiguration;
        this.jsonMapper = jsonMapper;
    }

    @Bean
    @BootstrapContextCompatible
    @Primary
    protected HttpClient httpClient(
        @Nullable InjectionPoint<?> injectionPoint,
        @Parameter @Nullable LoadBalancer loadBalancer,
        @Parameter @Nullable HttpClientConfiguration configuration
    ) {
        return resolveDefaultHttpClient(injectionPoint, loadBalancer, configuration);
    }

    private HttpClient resolveDefaultHttpClient(
        @Nullable InjectionPoint<?> injectionPoint,
        @Nullable LoadBalancer loadBalancer,
        @Nullable HttpClientConfiguration configuration
    ) {
        if (loadBalancer == null) {
            return getClient(injectionPoint != null ? injectionPoint.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);
        } else if (loadBalancer instanceof FixedLoadBalancer fixedLoadBalancer) {
            if (configuration == null) {
                configuration = defaultHttpClientConfiguration;
            }
            return new JavanetHttpClient(fixedLoadBalancer.getUri(), configuration);
        } else {
            throw new UnsupportedOperationException("Unsupported load balancer type: " + loadBalancer);
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

            return new JavanetHttpClient(contextPath == null ? null : URI.create(contextPath), configuration);
        });
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
