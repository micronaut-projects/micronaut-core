package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.util.HttpHeadersUtil;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;

/**
 * Implementation of {@link RawHttpClient} for the JDK http client.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
final class JdkRawHttpClient extends AbstractJdkHttpClient implements RawHttpClient {
    public JdkRawHttpClient(AbstractJdkHttpClient prototype) {
        super(prototype);
    }

    @Override
    public @NonNull Publisher<? extends HttpResponse<?>> exchange(@NonNull HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread) {
        return exchangeImpl(new RawHttpRequestWrapper(conversionService, request.toMutableRequest(), requestBody), null);
    }

    @Override
    public void close() throws IOException {
        // Nothing to do here, we do not need to close clients
    }

    @Override
    protected <O> Publisher<HttpResponse<O>> responsePublisher(@NonNull HttpRequest<?> request, @Nullable Argument<O> bodyType) {
        return Mono.defer(() -> mapToHttpRequest(request, bodyType)) // defered so any client filter changes are used
            .map(httpRequest -> {
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Sending HTTP Request: {}", clientId, httpRequest);
                }
                HttpHeadersUtil.trace(log,
                    () -> httpRequest.headers().map().keySet(),
                    headerName -> httpRequest.headers().allValues(headerName));
                return client.sendAsync(httpRequest, responseInfo -> new ReactiveByteBufferByteBody.ByteBodySubscriber(new BodySizeLimits(Long.MAX_VALUE, configuration.getMaxContentLength())));
            })
            .flatMap(Mono::fromCompletionStage)
            .onErrorMap(IOException.class, e -> new HttpClientException("Error sending request: " + e.getMessage(), e))
            .onErrorMap(InterruptedException.class, e -> new HttpClientException("Error sending request: " + e.getMessage(), e))
            .map(netResponse -> {
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Received HTTP Response: {} {}", clientId, netResponse.statusCode(), netResponse.uri());
                }

                //noinspection unchecked
                return (HttpResponse<O>) ByteBodyHttpResponseWrapper.wrap(new BaseHttpResponseAdapter<CloseableByteBody, O>(netResponse, conversionService) {
                    @Override
                    public @NonNull Optional<O> getBody() {
                        return Optional.empty();
                    }
                }, netResponse.body());
            });
    }
}
