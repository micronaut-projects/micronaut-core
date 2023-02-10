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
package io.micronaut.http.client.javanet;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;

/*
 * TODO: HttpClient defaults to netty ByteBuffer as a type for exchange which isn't best for here.
 */
@Experimental
abstract class AbstractJavanetHttpClient {

    protected final LoadBalancer loadBalancer;
    protected final HttpVersionSelection httpVersion;
    protected final HttpClientConfiguration configuration;
    protected final String contextPath;
    protected final HttpClient client;
    protected final CookieManager cookieManager;
    protected final RequestBinderRegistry requestBinderRegistry;
    protected final String clientId;
    protected final ConversionService conversionService;
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;

    private final Logger log;

    protected AbstractJavanetHttpClient(
        Logger log,
        LoadBalancer loadBalancer,
        HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        String contextPath,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        RequestBinderRegistry requestBinderRegistry,
        String clientId,
        ConversionService conversionService
    ) {
        this.log = log;
        this.loadBalancer = loadBalancer;
        this.httpVersion = httpVersion;
        this.configuration = configuration;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.requestBinderRegistry = requestBinderRegistry;
        this.clientId = clientId;
        this.conversionService = conversionService;
        this.cookieManager = new CookieManager();

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

        builder
            .version(httpVersion != null && httpVersion.isAlpn() ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1)
            .followRedirects(configuration.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
            .cookieHandler(cookieManager);

        Optional<SocketAddress> proxyAddress = configuration.getProxyAddress();
        if (proxyAddress.isPresent()) {
            SocketAddress socketAddress = proxyAddress.get();
            if (log.isDebugEnabled()) {
                log.debug("Configuring proxy: {}", socketAddress);
            }
            builder = configureProxy(builder, socketAddress, configuration.getProxyUsername().orElse(null), configuration.getProxyPassword().orElse(null));
        }

        if (configuration.getSslConfiguration() instanceof ClientSslConfiguration clientSslConfiguration) {
            builder = builder.sslContext(configureSsl(clientSslConfiguration));
        }

        this.client = builder.build();
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

    private SSLContext configureSsl(ClientSslConfiguration clientSslConfiguration) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            if (clientSslConfiguration.isInsecureTrustAllCertificates()) {
                TrustManager[] trustAllCerts = new TrustManager[]{new TrustAllTrustManager()};
                sslContext.init(null, trustAllCerts, null);
            } else {
                sslContext = SSLContext.getDefault();
            }
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new SslConfigurationException(e);
        }
    }

    protected <O> HttpResponse<O> getConvertedResponse(java.net.http.HttpResponse<byte[]> httpResponse, @NonNull Argument<O> bodyType) {
        return new HttpResponse<O>() {
            @Override
            public HttpStatus getStatus() {
                return HttpStatus.valueOf(httpResponse.statusCode());
            }

            @Override
            public int code() {
                return httpResponse.statusCode();
            }

            @Override
            public String reason() {
                throw new UnsupportedOperationException("Not implemented yet");
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeadersAdapter(httpResponse.headers(), conversionService);
            }

            @Override
            public MutableConvertibleValues<Object> getAttributes() {
                return null;
            }

            @Override
            public Optional<O> getBody() {
                return convertBytes(getContentType().orElse(null), httpResponse.body(), bodyType);
            }
        };
    }

    protected Object getLoadBalancerDiscriminator() {
        return null;
    }

    private <T> Optional convertBytes(@Nullable MediaType contentType, byte[] bytes, Argument<T> type) {
        if (type != null && mediaTypeCodecRegistry != null && contentType != null) {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                Charset charset = contentType.getCharset().orElse(StandardCharsets.UTF_8);
                return Optional.of(new String(bytes, charset));
            } else if (type.getType() == byte[].class) {
                return Optional.of(bytes);
            } else {
                Optional<MediaTypeCodec> foundCodec = mediaTypeCodecRegistry.findCodec(contentType);
                if (foundCodec.isPresent()) {
                    MediaTypeCodec codec = foundCodec.get();
                    return Optional.of(codec.decode(type, bytes));
                }
            }
        }
        // last chance, try type conversion
        return type != null ? conversionService.convert(bytes, ConversionContext.of(type)) : Optional.empty();
    }

    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    protected <I> Flux<HttpRequest> mapToHttpRequest(io.micronaut.http.HttpRequest<I> request, Argument<?> bodyType) {
        return Flux.from(loadBalancer.select(getLoadBalancerDiscriminator()))
            .map(server -> {
                request.getCookies().getAll().forEach(cookie -> {
                    HttpCookie newCookie = HttpCookieUtils.of(cookie, request, server);
                    cookieManager.getCookieStore().add(server.getURI(), newCookie);
                });

                try {
                    return server.resolve(ContextPathUtils.prepend(request.getUri(), contextPath));
                } catch (URISyntaxException e) {
                    throw HttpClientExceptionUtils.populateServiceId(new HttpClientException("Failed to construct the request URI", e), clientId, configuration);
                }
            })
            .map(uri -> HttpRequestFactory.builder(uri, request, configuration, bodyType, mediaTypeCodecRegistry).build());
    }


    @SuppressWarnings("java:S4830") // This is explicitly to turn security off when isInsecureTrustAllCertificates
    private static class TrustAllTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // trust everything
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // trust everything
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
