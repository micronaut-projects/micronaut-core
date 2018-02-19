/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.jackson.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.io.buffer.ByteBufferFactory;
import org.particleframework.core.type.Argument;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.CodecException;
import org.particleframework.http.codec.MediaTypeCodec;
import org.particleframework.runtime.ApplicationConfiguration;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link MediaTypeCodec} for JSON and Jackson
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class JsonMediaTypeCodec implements MediaTypeCodec {
    private final ObjectMapper objectMapper;
    private final ApplicationConfiguration applicationConfiguration;

    public JsonMediaTypeCodec(ObjectMapper objectMapper, ApplicationConfiguration applicationConfiguration) {
        this.objectMapper = objectMapper;
        this.applicationConfiguration = applicationConfiguration;
    }

    /**
     * @return The object mapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public boolean supportsType(Class<?> type) {
        return !(CharSequence.class.isAssignableFrom(type));
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_JSON_TYPE;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        try {
            if(type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return objectMapper.readValue(inputStream, javaType);
            }
            else {
                return objectMapper.readValue(inputStream, type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type ["+type.getName()+"]: " + e.getMessage());
        }
    }

    public <T> T decode(Argument<T> type, JsonNode node) throws CodecException {
        try {
            T result = objectMapper.treeToValue(node, type.getType());
            return result;
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type ["+type.getName()+"]: " + e.getMessage());
        }
    }
    @Override
    public <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
        try {
            if(CharSequence.class.isAssignableFrom(type.getType())) {
                return (T) buffer.toString(applicationConfiguration.getDefaultCharset());
            }
            else if(type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return objectMapper.readValue(buffer.toByteArray(), javaType);
            }
            else {
                return objectMapper.readValue(buffer.toByteArray(), type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type ["+type.getName()+"]: " + e.getMessage());
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T> T decode(Argument<T> type, String data) throws CodecException {
        try {
            if(type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return objectMapper.readValue(data, javaType);
            }
            else {
                return objectMapper.readValue(data, type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type ["+type.getName()+"]: " + e.getMessage());
        }
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try {
            objectMapper.writeValue(outputStream, object);
        } catch (IOException e) {
            throw new CodecException("Error encoding object ["+object+"] to JSON: " + e.getMessage());
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        try {
            if(object instanceof byte[]) {
                return (byte[]) object;
            }
            else {
                return objectMapper.writeValueAsBytes(object);
            }
        } catch (JsonProcessingException e) {
            throw new CodecException("Error encoding object ["+object+"] to JSON: " + e.getMessage());
        }
    }

    @Override
    public <T> ByteBuffer encode(T object, ByteBufferFactory allocator) throws CodecException {
        byte[] bytes = encode(object);
        return allocator.copiedBuffer(bytes);
    }

    private <T> JavaType constructJavaType(Argument<T> type) {
        Map<String, Argument<?>> typeVariables = type.getTypeVariables();
        TypeFactory typeFactory = objectMapper.getTypeFactory();
        JavaType[] objects = toJavaTypeArray(typeFactory, typeVariables);
        return typeFactory.constructParametricType(
                type.getType(),
                objects
        );
    }

    private JavaType[] toJavaTypeArray(TypeFactory typeFactory, Map<String, Argument<?>> typeVariables) {
        List<JavaType> javaTypes = new ArrayList<>();
        for (Argument<?> argument : typeVariables.values()) {
            if(argument.hasTypeVariables()) {
                javaTypes.add(typeFactory.constructParametricType(argument.getType(), toJavaTypeArray(typeFactory, argument.getTypeVariables())));
            }
            else {
                javaTypes.add(typeFactory.constructType(argument.getType()));
            }
        }
        return javaTypes.toArray(new JavaType[javaTypes.size()]);
    }
}
