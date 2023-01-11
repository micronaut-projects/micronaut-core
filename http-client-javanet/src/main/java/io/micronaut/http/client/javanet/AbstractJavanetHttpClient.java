/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.cookie.Cookie;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * TODO: HttpClient defaults to netty ByteBuffer as a type for exchange which isn't best for here
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
    private final Logger log;
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;

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
        if (StringUtils.isNotEmpty(contextPath)) {
            if (contextPath.charAt(0) != '/') {
                contextPath = '/' + contextPath;
            }
            this.contextPath = contextPath;
        } else {
            this.contextPath = null;
        }
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.requestBinderRegistry = requestBinderRegistry;
        this.clientId = clientId;
        this.conversionService = conversionService;
        this.cookieManager = new CookieManager();
        this.client = java.net.http.HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(configuration.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
            .cookieHandler(cookieManager)
            .build();
    }

    // TODO: Extract from DefaultHttpClient
    static void customizeException0(HttpClientConfiguration configuration, String informationalServiceId, HttpClientException exc) {
        if (informationalServiceId != null) {
            exc.setServiceId(informationalServiceId);
        } else if (configuration instanceof ServiceHttpClientConfiguration clientConfiguration) {
            exc.setServiceId(clientConfiguration.getServiceId());
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
                return new HttpHeadersAdapter(httpResponse.headers());
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
                request.getCookies().getAll().forEach(cookie -> cookieConverter(cookie, request, server));
                return server.resolve(prependContextPath(request.getUri()));
            })
            .map(uri -> HttpRequestFactory.builder(uri, request, conversionService, bodyType, mediaTypeCodecRegistry).build());
    }

    private <I> void cookieConverter(Cookie cookie, io.micronaut.http.HttpRequest<I> request, ServiceInstance server) {
        HttpCookie newCookie = new HttpCookie(cookie.getName(), cookie.getValue());
        newCookie.setMaxAge(cookie.getMaxAge());
        newCookie.setDomain(server.getHost());
        newCookie.setHttpOnly(cookie.isHttpOnly());
        newCookie.setSecure(cookie.isSecure());
        newCookie.setPath(cookie.getPath() == null ? request.getPath() : cookie.getPath());
        cookieManager.getCookieStore().add(server.getURI(), newCookie);
    }

    /**
     * @param requestURI The request URI
     * @return A URI that is prepended with the contextPath, if set
     */
    // TODO: Extract from DefaultHttpClient
    protected URI prependContextPath(URI requestURI) {
        if (StringUtils.isNotEmpty(contextPath)) {
            try {
                return new URI(StringUtils.prependUri(contextPath, requestURI.toString()));
            } catch (URISyntaxException e) {
                throw customizeException(new HttpClientException("Failed to construct the request URI", e));
            }
        }
        return requestURI;
    }

    // TODO: Extract from DefaultHttpClient
    protected <E extends HttpClientException> E customizeException(E exc) {
        customizeException0(configuration, clientId, exc);
        return exc;
    }
}
