/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.jackson.databind;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.jackson.ObjectMapperFactory;
import io.micronaut.jackson.codec.JacksonFeatures;
import io.micronaut.jackson.core.parser.JacksonCoreParserFactory;
import io.micronaut.jackson.core.parser.JacksonCoreProcessor;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.jackson.core.tree.TreeGenerator;
import io.micronaut.jackson.serialize.JsonNodeDeserializer;
import io.micronaut.jackson.serialize.JsonNodeSerializer;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * jackson-databind implementation of {@link JsonMapper}.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Internal
@Singleton
@BootstrapContextCompatible
public final class JacksonDatabindMapper implements JsonMapper {
    private final ObjectMapper objectMapper;
    private final JsonStreamConfig config;
    private final JsonNodeTreeCodec treeCodec;
    private final ObjectReader specializedReader;
    private final ObjectWriter specializedWriter;

    @Inject
    @Internal
    public JacksonDatabindMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.config = JsonStreamConfig.DEFAULT
                .withUseBigDecimalForFloats(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS))
                .withUseBigIntegerForInts(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS));
        this.treeCodec = JsonNodeTreeCodec.getInstance().withConfig(config);
        this.specializedReader = null;
        this.specializedWriter = null;
    }

    @Internal
    public JacksonDatabindMapper() {
        this(createDefaultMapper());
    }

    private static ObjectMapper createDefaultMapper() {
        ObjectMapperFactory objectMapperFactory = new ObjectMapperFactory();
        objectMapperFactory.setDeserializers(new JsonNodeDeserializer());
        objectMapperFactory.setSerializers(new JsonNodeSerializer());
        return objectMapperFactory.objectMapper(null, null);
    }

    private JacksonDatabindMapper(JacksonDatabindMapper from, Argument<?> type) {
        this.objectMapper = from.objectMapper;
        this.config = from.config;
        this.treeCodec = from.treeCodec;
        this.specializedReader = from.createReader(type);
        this.specializedWriter = from.createWriter(type);
    }

    @Internal
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public JsonMapper createSpecific(Argument<?> type) {
        return new JacksonDatabindMapper(this, type);
    }

    private ObjectReader createReader(@NonNull Argument<?> type) {
        if (specializedReader != null) {
            return specializedReader;
        }
        ObjectReader reader = objectMapper.readerFor(JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
        @SuppressWarnings("rawtypes")
        Optional<Class> view = type.getAnnotationMetadata().classValue(JsonView.class);
        if (view.isPresent()) {
            reader = reader.withView(view.get());
        }
        return reader;
    }

    private ObjectWriter createWriter(@NonNull Argument<?> type) {
        if (specializedWriter != null) {
            return specializedWriter;
        }
        ObjectWriter writer = objectMapper.writerFor(JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
        @SuppressWarnings("rawtypes")
        Optional<Class> view = type.getAnnotationMetadata().classValue(JsonView.class);
        if (view.isPresent()) {
            writer = writer.withView(view.get());
        }
        return writer;
    }

    @Override
    public <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException {
        return createReader(type).readValue(treeAsTokens(tree));
    }

    @Override
    public @NonNull JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        treeGenerator.setCodec(objectMapper);
        objectMapper.writeValue(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @NonNull
    @Override
    public <T> JsonNode writeValueToTree(@NonNull Argument<T> type, T value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        treeGenerator.setCodec(objectMapper);
        createWriter(type).writeValue(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException {
        try {
            return createReader(type).readValue(inputStream);
        } catch (JsonParseException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T readValue(@NonNull byte[] byteArray, @NonNull Argument<T> type) throws IOException {
        try {
            return createReader(type).readValue(byteArray);
        } catch (JsonParseException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T readValue(ByteBuffer<?> byteBuffer, Argument<T> type) throws IOException {
        try (JsonParser parser = JacksonCoreParserFactory.createJsonParser(objectMapper.getFactory(), byteBuffer)) {
            return createReader(type).readValue(parser);
        } catch (JsonParseException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException {
        objectMapper.writeValue(outputStream, object);
    }

    @Override
    public <T> void writeValue(@NonNull OutputStream outputStream, @NonNull Argument<T> type, T object) throws IOException {
        createWriter(type).writeValue(outputStream, object);
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        return objectMapper.writeValueAsBytes(object);
    }

    @Override
    public <T> byte[] writeValueAsBytes(@NonNull Argument<T> type, T object) throws IOException {
        return createWriter(type).writeValueAsBytes(object);
    }

    @Override
    public void updateValueFromTree(Object value, @NonNull JsonNode tree) throws IOException {
        objectMapper.readerForUpdating(value).readValue(treeAsTokens(tree));
    }

    @Override
    public @NonNull JsonMapper cloneWithFeatures(@NonNull JsonFeatures features) {
        JacksonFeatures jacksonFeatures = (JacksonFeatures) features;

        ObjectMapper objectMapper = this.objectMapper.copy();
        jacksonFeatures.getDeserializationFeatures().forEach(objectMapper::configure);
        jacksonFeatures.getSerializationFeatures().forEach(objectMapper::configure);
        for (Class<? extends Module> moduleClass : jacksonFeatures.getAdditionalModules()) {
            objectMapper.registerModule(InstantiationUtils.instantiate(moduleClass));
        }

        return new JacksonDatabindMapper(objectMapper);
    }

    @NonNull
    @Override
    public JsonMapper cloneWithViewClass(@NonNull Class<?> viewClass) {
        ObjectMapper objectMapper = this.objectMapper.copy();
        objectMapper.setConfig(objectMapper.getSerializationConfig().withView(viewClass));
        objectMapper.setConfig(objectMapper.getDeserializationConfig().withView(viewClass));

        return new JacksonDatabindMapper(objectMapper);
    }

    @NonNull
    @Override
    public JsonStreamConfig getStreamConfig() {
        return config;
    }

    @Override
    public @NonNull Processor<byte[], JsonNode> createReactiveParser(@NonNull Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new JacksonCoreProcessor(streamArray, objectMapper.getFactory(), config) {
            @Override
            public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                onSubscribe.accept(this);
                super.subscribe(downstreamSubscriber);
            }
        };
    }

    @NonNull
    @Override
    public Optional<JsonFeatures> detectFeatures(@NonNull AnnotationMetadata annotations) {
        return Optional.ofNullable(annotations.getAnnotation(io.micronaut.jackson.annotation.JacksonFeatures.class))
                .map(JacksonFeatures::fromAnnotation);
    }

    private JsonParser treeAsTokens(@NonNull JsonNode tree) {
        JsonParser parser = treeCodec.treeAsTokens(tree);
        parser.setCodec(objectMapper);
        return parser;
    }
}
