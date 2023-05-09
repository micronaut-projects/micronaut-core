package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

@Internal
public final class DynamicWriter implements MessageBodyWriter<Object> {
    private final MessageBodyHandlerRegistry registry;
    private final List<MediaType> mediaTypes;

    public DynamicWriter(MessageBodyHandlerRegistry registry, List<MediaType> mediaTypes) {
        this.registry = registry;
        this.mediaTypes = mediaTypes;
    }

    @Override
    public MessageBodyWriter<Object> createSpecific(Argument<Object> type) {
        return registry.findWriter(type, mediaTypes).orElse(this);
    }

    public MessageBodyWriter<Object> find(Argument<Object> type, MediaType mediaType, Object object) {
        Optional<MessageBodyWriter<Object>> specific = registry.findWriter(type, List.of(mediaType));
        if (specific.isPresent() && !(specific.get() instanceof DynamicWriter)) {
            return specific.get();
        }
        Argument<?> dynamicType = Argument.of(object.getClass());
        Optional<? extends MessageBodyWriter<?>> dynamicWriter = registry.findWriter(dynamicType, List.of(mediaType));
        if (dynamicWriter.isPresent() && !(dynamicWriter.get() instanceof DynamicWriter)) {
            //noinspection unchecked
            return (MessageBodyWriter<Object>) dynamicWriter.get();
        }
        if (mediaType.equals(MediaType.TEXT_PLAIN_TYPE) && ClassUtils.isJavaLangType(object.getClass())) {
            // compatibility...
            // this will fall back to RawStringHandler, which can handle Object.
            //noinspection unchecked,OptionalGetWithoutIsPresent,rawtypes
            return (MessageBodyWriter) registry.findWriter(Argument.STRING, List.of(MediaType.TEXT_PLAIN_TYPE)).get();
        }
        throw new CodecException("Cannot encode value [" + object + "]. No possible encoders found");
    }

    @Override
    public void writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        find(type, mediaType, object).writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        return find(type, mediaType, object).writeTo(type, mediaType, object, outgoingHeaders, bufferFactory);
    }
}
