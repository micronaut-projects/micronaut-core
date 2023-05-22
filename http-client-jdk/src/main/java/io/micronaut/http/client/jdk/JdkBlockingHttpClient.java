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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.jdk.cookie.CookieDecoder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;

/**
 * {@link io.micronaut.http.client.HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public class JdkBlockingHttpClient extends AbstractJdkHttpClient implements BlockingHttpClient {

    public JdkBlockingHttpClient(
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
        super(
            configuration.getLoggerName().map(LoggerFactory::getLogger).orElseGet(() -> LoggerFactory.getLogger(JdkBlockingHttpClient.class)),
            loadBalancer,
            httpVersion,
            configuration,
            contextPath,
            mediaTypeCodecRegistry,
            requestBinderRegistry,
            clientId,
            conversionService,
            sslBuilder,
            cookieDecoder
        );
    }

    @Override
    public <I, O, E> io.micronaut.http.HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request,
                                              Argument<O> bodyType,
                                              Argument<E> errorType) {
        var httpRequest = mapToHttpRequest(request, bodyType).block();
        try {
            if (log.isDebugEnabled()) {
                log.debug("Client {} Sending HTTP Request: {}", clientId, httpRequest);
            }
            HttpResponse<byte[]> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            boolean errorStatus = httpResponse.statusCode() >= 400;
            if (errorStatus && configuration.isExceptionOnErrorStatus()) {
                if (log.isErrorEnabled()) {
                    log.error("Client {} Received HTTP Response: {} {}", clientId, httpResponse.statusCode(), httpResponse.uri());
                }
                throw HttpClientExceptionUtils.populateServiceId(new HttpClientResponseException(HttpStatus.valueOf(httpResponse.statusCode()).getReason(), response(httpResponse, bodyType)), clientId, configuration);
            }
            return response(httpResponse, bodyType);
        } catch (IOException e) {
            throw new HttpClientException("Error sending request: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpClientException("Error sending request: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        // Nothing to do here, we do not need to close clients
    }
}
