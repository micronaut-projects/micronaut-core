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

/**
 * Placeholder {@link MessageBodyWriter} implementation that decides which writer to use based on
 * the dynamic (runtime) type of the body. Used as fallback where the type is not known statically.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class DynamicMessageBodyWriter implements MessageBodyWriter<Object> {
    private final MessageBodyHandlerRegistry registry;
    private final List<MediaType> mediaTypes;

    public DynamicMessageBodyWriter(MessageBodyHandlerRegistry registry, List<MediaType> mediaTypes) {
        this.registry = registry;
        this.mediaTypes = mediaTypes;
    }

    @Override
    public MessageBodyWriter<Object> createSpecific(Argument<Object> type) {
        return registry.findWriter(type, mediaTypes).orElse(this);
    }

    public MessageBodyWriter<Object> find(Argument<Object> type, MediaType mediaType, Object object) {
        MessageBodyWriter<Object> specific = registry.findWriter(type, List.of(mediaType)).orElse(null);
        if (specific != null && !(specific instanceof DynamicMessageBodyWriter)) {
            return specific;
        }
        Argument<?> dynamicType = Argument.of(object.getClass());
        MessageBodyWriter<?> dynamicWriter = registry.findWriter(dynamicType, List.of(mediaType)).orElse(null);
        if (dynamicWriter != null && !(dynamicWriter instanceof DynamicMessageBodyWriter)) {
            //noinspection unchecked
            return (MessageBodyWriter<Object>) dynamicWriter;
        }
        if (mediaType.equals(MediaType.TEXT_PLAIN_TYPE) && ClassUtils.isJavaLangType(object.getClass())) {
            // compatibility...
            // this will fall back to RawStringHandler, which can handle Object.
            //noinspection unchecked,OptionalGetWithoutIsPresent,rawtypes
            return (MessageBodyWriter) registry.findWriter(Argument.STRING, List.of(MediaType.TEXT_PLAIN_TYPE)).get();
        }
        throw new CodecException("Cannot encode value [" + object + "]. No possible encoders found for medata type: " + mediaType);
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
