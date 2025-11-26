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
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.cfg.MapperBuilder;

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

    /**
     * Property used to specify whether JSON view is enabled.
     */
    public static final String PROPERTY_JSON_VIEW_ENABLED = "jackson.json-view.enabled";

    private final ObjectMapper objectMapper;
    private final JsonStreamConfig config;
    private final JsonNodeTreeCodec treeCodec;
    private final ObjectReader specializedReader;
    private final ObjectWriter specializedWriter;
    private final boolean allowViews;

    private TypeCache<ObjectReader> cachedReader;
    private TypeCache<ObjectWriter> cachedWriter;

    @Internal
    public JacksonDatabindMapper(ObjectMapper objectMapper) {
        this(objectMapper, false);
    }

    @Inject
    @Internal
    public JacksonDatabindMapper(ObjectMapper objectMapper, @Value("${" + JacksonDatabindMapper.PROPERTY_JSON_VIEW_ENABLED + ":false}") boolean allowViews) {
        this.objectMapper = objectMapper;
        this.allowViews = allowViews;
        this.config = JsonStreamConfig.DEFAULT
            .withUseBigDecimalForFloats(objectMapper.deserializationConfig().isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS))
            .withUseBigIntegerForInts(objectMapper.deserializationConfig().isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS));
        this.treeCodec = JsonNodeTreeCodec.getInstance().withConfig(config);
        this.specializedReader = null;
        this.specializedWriter = null;
    }

    @Internal
    public JacksonDatabindMapper() {
        this(createDefaultMapper(), false);
    }

    private JacksonDatabindMapper(JacksonDatabindMapper from, Argument<?> type, boolean allowViews) {
        this.objectMapper = from.objectMapper;
        this.config = from.config;
        this.treeCodec = from.treeCodec;
        this.specializedReader = from.createReader(type);
        this.specializedWriter = from.createWriter(type);
        this.allowViews = allowViews;
    }

    private JacksonDatabindMapper(JacksonDatabindMapper from, ObjectReader reader, ObjectWriter writer) {
        this.objectMapper = from.objectMapper;
        this.config = from.config;
        this.treeCodec = from.treeCodec;
        this.specializedReader = reader;
        this.specializedWriter = writer;
        this.allowViews = from.allowViews;
    }

    private static ObjectMapper createDefaultMapper() {
        var objectMapperFactory = new ObjectMapperFactory();
        objectMapperFactory.setDeserializers(new JsonNodeDeserializer());
        objectMapperFactory.setSerializers(new JsonNodeSerializer());
        return objectMapperFactory.objectMapper(null, null);
    }

    @Internal
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public @NonNull JsonMapper createSpecific(@NonNull Argument<?> type) {
        JacksonDatabindMapper jacksonDatabindMapper = new JacksonDatabindMapper(this, type, allowViews);
        if (allowViews) {
            Class<?> viewClass = type.getAnnotationMetadata().classValue(JsonView.class).orElse(null);
            if (viewClass != null) {
                return jacksonDatabindMapper.cloneWithViewClass(viewClass);
            }
        }
        return jacksonDatabindMapper;
    }

    private ObjectReader createReader(@NonNull Argument<?> type) {
        if (specializedReader != null) {
            return specializedReader;
        }
        TypeCache<ObjectReader> cachedReader = this.cachedReader;
        if (cachedReader != null && cachedReader.type == type) {
            return cachedReader.cachedValue;
        }
        ObjectReader reader = objectMapper.readerFor(JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
        @SuppressWarnings("rawtypes")
        Optional<Class> view = type.getAnnotationMetadata().classValue(JsonView.class);
        if (view.isPresent()) {
            reader = reader.withView(view.get());
        }
        this.cachedReader = new TypeCache<>(type, reader);
        return reader;
    }

    private ObjectWriter createWriter(@NonNull Argument<?> type) {
        if (specializedWriter != null) {
            return specializedWriter;
        }
        TypeCache<ObjectWriter> cachedWriter = this.cachedWriter;
        if (cachedWriter != null && cachedWriter.type == type) {
            return cachedWriter.cachedValue;
        }
        ObjectWriter writer = objectMapper.writerFor(JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
        @SuppressWarnings("rawtypes")
        Optional<Class> view = type.getAnnotationMetadata().classValue(JsonView.class);
        if (view.isPresent()) {
            writer = writer.withView(view.get());
        }
        this.cachedWriter = new TypeCache<>(type, writer);
        return writer;
    }

    @Override
    public <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException {
        return createReader(type).readValue(treeAsTokens(tree));
    }

    @Override
    public @NonNull JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        objectMapper.writeValue(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @NonNull
    @Override
    public <T> JsonNode writeValueToTree(@NonNull Argument<T> type, T value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        createWriter(type).writeValue(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException {
        try {
            return createReader(type).readValue(inputStream);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T readValue(byte @NonNull [] byteArray, @NonNull Argument<T> type) throws IOException {
        try {
            return createReader(type).readValue(byteArray);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T readValue(@NonNull ByteBuffer<?> byteBuffer, @NonNull Argument<T> type) throws IOException {
        try (JsonParser parser = JacksonCoreParserFactory.createJsonParser((JsonFactory) objectMapper.tokenStreamFactory(), byteBuffer)) {
            return createReader(type).readValue(parser);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException {
        if (specializedWriter != null) {
            specializedWriter.writeValue(outputStream, object);
        } else {
            objectMapper.writeValue(outputStream, object);
        }
    }

    @Override
    public <T> void writeValue(@NonNull OutputStream outputStream, @NonNull Argument<T> type, T object) throws IOException {
        createWriter(type).writeValue(outputStream, object);
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        if (specializedWriter != null) {
            return specializedWriter.writeValueAsBytes(object);
        }
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

        MapperBuilder<?, ?> builder = objectMapper.rebuild();
        jacksonFeatures.getDeserializationFeatures().forEach(builder::configure);
        jacksonFeatures.getSerializationFeatures().forEach(builder::configure);
        for (Class<? extends JacksonModule> moduleClass : jacksonFeatures.getAdditionalModules()) {
            builder.addModule(InstantiationUtils.instantiate(moduleClass));
        }

        return new JacksonDatabindMapper(builder.build(), allowViews);
    }

    @NonNull
    @Override
    public JsonMapper cloneWithViewClass(@NonNull Class<?> viewClass) {
        ObjectReader reader = objectMapper.readerWithView(viewClass);
        ObjectWriter writer = objectMapper.writerWithView(viewClass);

        return new JacksonDatabindMapper(this, reader, writer);
    }

    @NonNull
    @Override
    public JsonStreamConfig getStreamConfig() {
        return config;
    }

    @Override
    public @NonNull Processor<byte[], JsonNode> createReactiveParser(@NonNull Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new JacksonCoreProcessor(streamArray, objectMapper.tokenStreamFactory(), config) {
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
        DeserializationContext context = objectMapper._deserializationContext(); // Not supposed to be used technically
        return treeCodec.treeAsTokens(tree, context);
    }

    private record TypeCache<T>(Argument<?> type, T cachedValue) {
    }
}
