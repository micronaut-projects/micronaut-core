package io.micronaut.http.client.javanet;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

abstract sealed class AbstractJavanetHttpClient permits JavanetHttpClient, JavanetBlockingHttpClient {

    protected final URI uri;

    @Nullable
    protected final HttpClientConfiguration httpClientConfiguration;
    @Nullable
    protected final MediaTypeCodecRegistry mediaTypeCodecRegistry;

    AbstractJavanetHttpClient(
        URI uri,
        @Nullable HttpClientConfiguration httpClientConfiguration,
        @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry
    ) {
        this.uri = uri;
        this.httpClientConfiguration = httpClientConfiguration;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
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

    private <T> Optional convertBytes(@Nullable MediaType contentType, byte[] bytes, Argument<T> type) {
        if (mediaTypeCodecRegistry != null && contentType != null) {
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
        return ConversionService.SHARED.convert(bytes, ConversionContext.of(type));
    }
}
