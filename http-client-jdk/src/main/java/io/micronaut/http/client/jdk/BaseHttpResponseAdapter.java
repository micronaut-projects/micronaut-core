package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;

/**
 * Base {@link HttpResponse} implementation for the JDK client, for streaming and buffered
 * responses.
 *
 * @param <B> The JDK body type
 * @param <O> The Micronaut HttpResponse body type
 */
@Internal
abstract class BaseHttpResponseAdapter<B, O> implements HttpResponse<O> {
    final java.net.http.HttpResponse<B> httpResponse;
    final ConversionService conversionService;
    final MutableConvertibleValues<Object> attributes = new MutableConvertibleValuesMap<>();

    BaseHttpResponseAdapter(java.net.http.HttpResponse<B> httpResponse,
                               ConversionService conversionService) {
        this.httpResponse = httpResponse;
        this.conversionService = conversionService;
    }

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
        return getStatus().getReason();
    }

    @Override
    public HttpHeaders getHeaders() {
        return new HttpHeadersAdapter(httpResponse.headers(), conversionService);
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }
}
