package io.micronaut.json.message;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.io.OutputStream;

@Singleton
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class JsonMessageHandler<T> implements MessageBodyHandler<T> {
    private final JsonMediaTypeCodec jsonMediaTypeCodec;

    public JsonMessageHandler(JsonMediaTypeCodec jsonMediaTypeCodec) {
        this.jsonMediaTypeCodec = jsonMediaTypeCodec;
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return true;
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, HttpHeaders httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return jsonMediaTypeCodec.decode(
            type,
            byteBuffer
        );
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, HttpHeaders httpHeaders, InputStream inputStream) throws CodecException {
        return jsonMediaTypeCodec.decode(
            type,
            inputStream
        );
    }

    @Override
    public boolean isWriteable(Argument<T> type, MediaType mediaType) {
        return true;
    }

    @Override
    public void writeTo(Argument<T> type, T object, MediaType mediaType, MutableHttpHeaders httpHeaders, OutputStream outputStream) throws CodecException {
        httpHeaders.contentType(mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE);
        jsonMediaTypeCodec.encode(
            type,
            outputStream
        );
    }

    @Override
    public void writeTo(Argument<T> type, T object, MediaType mediaType, MutableHttpHeaders httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        httpHeaders.contentType(mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE);
        byteBuffer.write(
            jsonMediaTypeCodec.encode(
                type,
                object
            )
        );
    }
}
