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

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.jdk.cookie.CookieDecoder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.ClientSslConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.micronaut.http.client.exceptions.HttpClientExceptionUtils.populateServiceId;

/**
 * Abstract implementation of {@link DefaultJdkHttpClient} that provides common functionality.
 *
 * @author Sergio del Amo
 * @author Tim Yates
 * @since 4.0.0
 */
@Internal
@Experimental
abstract class AbstractJdkHttpClient {

    public static final String H2C_ERROR_MESSAGE = "H2C is not supported by the JDK HTTP client";
    public static final String H3_ERROR_MESSAGE = "HTTP/3 is not supported by the JDK HTTP client";
    public static final String WEIRD_ALPN_ERROR_MESSAGE = "The only supported ALPN modes are [" + HttpVersionSelection.ALPN_HTTP_1 + "] or [" + HttpVersionSelection.ALPN_HTTP_1 + "," + HttpVersionSelection.ALPN_HTTP_2 + "]";

    protected final LoadBalancer loadBalancer;
    protected final HttpVersionSelection httpVersion;
    protected final HttpClientConfiguration configuration;
    protected final String contextPath;
    protected final HttpClient client;
    protected final CookieManager cookieManager;
    protected final RequestBinderRegistry requestBinderRegistry;
    protected final String clientId;
    protected final ConversionService conversionService;
    protected final JdkClientSslBuilder sslBuilder;
    protected final Logger log;
    protected final CookieDecoder cookieDecoder;
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;

    /**
     * @param log                    the logger to use
     * @param loadBalancer           The {@link LoadBalancer} to use for selecting servers
     * @param httpVersion            The {@link HttpVersionSelection} to prefer
     * @param configuration          The {@link HttpClientConfiguration} to use
     * @param contextPath            The base URI to prepend to request uris
     * @param mediaTypeCodecRegistry The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param requestBinderRegistry  The request binder registry
     * @param clientId               The client id
     * @param conversionService      The {@link ConversionService}
     * @param sslBuilder             The {@link JdkClientSslBuilder} for creating an {@link javax.net.ssl.SSLContext}
     */
    protected AbstractJdkHttpClient(
        Logger log,
        LoadBalancer loadBalancer,
        HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        String contextPath,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        RequestBinderRegistry requestBinderRegistry,
        String clientId,
        ConversionService conversionService,
        JdkClientSslBuilder sslBuilder,
        CookieDecoder cookieDecoder
    ) {
        this.cookieDecoder = cookieDecoder;
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(log);
        this.loadBalancer = loadBalancer;
        this.httpVersion = httpVersion;
        this.configuration = configuration;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.requestBinderRegistry = requestBinderRegistry;
        this.clientId = clientId;
        this.conversionService = conversionService;
        this.cookieManager = new CookieManager();
        this.sslBuilder = sslBuilder;

        if (System.getProperty("jdk.internal.httpclient.disableHostnameVerification") != null && log.isWarnEnabled()) {
            log.warn("The jdk.internal.httpclient.disableHostnameVerification system property is set. This is not recommended for production use as it prevents proper certificate validation and may allow man-in-the-middle attacks.");
        }

        if (StringUtils.isNotEmpty(contextPath)) {
            if (contextPath.charAt(0) != '/') {
                contextPath = '/' + contextPath;
            }
            this.contextPath = contextPath;
        } else {
            this.contextPath = null;
        }

        HttpClient.Builder builder = HttpClient.newBuilder();
        configuration.getConnectTimeout().ifPresent(builder::connectTimeout);

        HttpVersionSelection httpVersionSelection = HttpVersionSelection.forClientConfiguration(configuration);

        if (httpVersionSelection.getPlaintextMode() == HttpVersionSelection.PlaintextMode.H2C) {
            throw new ConfigurationException(H2C_ERROR_MESSAGE);
        }
        if (httpVersionSelection.isHttp3()) {
            throw new ConfigurationException(H3_ERROR_MESSAGE);
        }

        if (httpVersionSelection.isAlpn()) {
            List<String> supportedProtocols = Arrays.asList(httpVersionSelection.getAlpnSupportedProtocols());
            if (supportedProtocols.size() == 2 &&
                supportedProtocols.contains(HttpVersionSelection.ALPN_HTTP_1) &&
                supportedProtocols.contains(HttpVersionSelection.ALPN_HTTP_2)) {
                builder.version(HttpClient.Version.HTTP_2);
            } else if (supportedProtocols.size() == 1 &&
                supportedProtocols.get(0).equals(HttpVersionSelection.ALPN_HTTP_1)) {
                builder.version(HttpClient.Version.HTTP_1_1);
            } else {
                throw new ConfigurationException(WEIRD_ALPN_ERROR_MESSAGE);
            }
        } else {
            builder.version(HttpClient.Version.HTTP_1_1);
        }

        builder
            .followRedirects(configuration.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
            .cookieHandler(cookieManager);

        Optional<SocketAddress> proxyAddress = configuration.getProxyAddress();
        if (proxyAddress.isPresent()) {
            SocketAddress socketAddress = proxyAddress.get();
            builder = configureProxy(builder, socketAddress, configuration.getProxyUsername().orElse(null), configuration.getProxyPassword().orElse(null));
        }

        if (configuration.getSslConfiguration() instanceof ClientSslConfiguration clientSslConfiguration) {
            configureSsl(builder, clientSslConfiguration);
        }

        this.client = builder.build();
    }

    private static HttpCookie toJdkCookie(@NonNull Cookie cookie,
                                          @NonNull io.micronaut.http.HttpRequest<?> request,
                                          @NonNull String host) {
        HttpCookie newCookie = new HttpCookie(cookie.getName(), cookie.getValue());
        newCookie.setMaxAge(cookie.getMaxAge());
        newCookie.setDomain(host);
        newCookie.setHttpOnly(cookie.isHttpOnly());
        newCookie.setSecure(cookie.isSecure());
        newCookie.setPath(cookie.getPath() == null ? request.getPath() : cookie.getPath());
        return newCookie;
    }

    private HttpClient.Builder configureProxy(
        @NonNull HttpClient.Builder builder,
        @NonNull SocketAddress address,
        @Nullable String username,
        @Nullable String password
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Configuring proxy: {} with username: {}", address, username);
        }
        if (address instanceof InetSocketAddress inetSocketAddress) {
            builder = builder.proxy(ProxySelector.of(inetSocketAddress));
            if (username != null && password != null) {
                builder = builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                });
            }
        } else {
            throw new IllegalArgumentException("Unsupported proxy address type: " + address.getClass().getName());
        }
        return builder;
    }

    private void configureSsl(HttpClient.Builder builder, ClientSslConfiguration clientSslConfiguration) {
        sslBuilder.build(clientSslConfiguration).ifPresent(builder::sslContext);
        SSLParameters sslParameters = new SSLParameters();
        clientSslConfiguration.getClientAuthentication().ifPresent(a -> {
            if (a == ClientAuthentication.WANT) {
                sslParameters.setWantClientAuth(true);
            } else if (a == ClientAuthentication.NEED) {
                sslParameters.setNeedClientAuth(true);
            }
        });
        clientSslConfiguration.getProtocols().ifPresent(sslParameters::setProtocols);
        clientSslConfiguration.getCiphers().ifPresent(sslParameters::setCipherSuites);
        builder.sslParameters(sslParameters);
    }

    /**
     * @return The {@link MediaTypeCodecRegistry}
     */
    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    /**
     * @param mediaTypeCodecRegistry The {@link MediaTypeCodecRegistry}
     */
    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    /**
     * Convert the Micronaut request to a JDK request.
     *
     * @param request  The Micronaut request object
     * @param bodyType The body type
     * @param <I>      The body type
     * @return A JDK request object
     */
    protected <I> Mono<HttpRequest> mapToHttpRequest(io.micronaut.http.HttpRequest<I> request, Argument<?> bodyType) {
        return resolveRequestUri(request)
            .map(uri -> {
                cookieDecoder.decode(request).ifPresent(cookies -> cookies.getAll().forEach(cookie -> {
                    HttpCookie newCookie = toJdkCookie(cookie, request, uri.getHost());
                    cookieManager.getCookieStore().add(uri, newCookie);
                }));

                return HttpRequestFactory.builder(uri, request, configuration, bodyType, mediaTypeCodecRegistry).build();
            });
    }

    private Mono<URI> resolveRequestUri(io.micronaut.http.HttpRequest<?> request) {
        if (request.getUri().getScheme() != null) {
            // Full request URI, so use that
            return Mono.just(request.getUri());
        }

        // Otherwise, go and look it up via the LoadBalancer
        return resolveURI(request);
    }

    private <I> Mono<URI> resolveURI(io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (loadBalancer == null) {
            return Mono.error(populateServiceId(new NoHostException("Request URI specifies no host to connect to"), clientId, configuration));
        }

        return Mono.from(loadBalancer.select(request)).map(server -> {
                Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                if (request instanceof MutableHttpRequest<?> mutableRequest && authInfo.isPresent()) {
                    mutableRequest.getHeaders().auth(authInfo.get());
                }

                try {
                    return server.resolve(ContextPathUtils.prepend(requestURI, contextPath));
                } catch (URISyntaxException e) {
                    throw populateServiceId(new HttpClientException("Failed to construct the request URI", e), clientId, configuration);
                }
            }
        );
    }

    /**
     * Convert the JDK response to a Micronaut response.
     *
     * @param netResponse The JDK response
     * @param bodyType    The body type
     * @param <O>         The body type
     * @return A Micronaut response
     */
    @NonNull
    protected <O> HttpResponse<O> response(@NonNull java.net.http.HttpResponse<byte[]> netResponse, @NonNull Argument<O> bodyType) {
        return new HttpResponseAdapter<>(netResponse, bodyType, conversionService, mediaTypeCodecRegistry);
    }
}
