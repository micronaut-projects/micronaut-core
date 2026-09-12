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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
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
import org.jspecify.annotations.Nullable;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;
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

    /**
     * Number of slots in the reader and writer caches. Must be a power of two. Each slot holds
     * the reader or writer for one type, selected by the type hash, so the caches never hold more
     * than this many entries regardless of how many distinct types are seen.
     */
    private static final int TYPE_CACHE_SIZE = 64;

    private final ObjectMapper objectMapper;
    private final JsonStreamConfig config;
    private final JsonNodeTreeCodec treeCodec;
    @Nullable
    private final ObjectReader specializedReader;
    @Nullable
    private final ObjectWriter specializedWriter;
    private final boolean allowViews;

    /**
     * Per-type caches, only allocated for a general mapper. A specialized mapper (one with a
     * {@link #specializedReader} and {@link #specializedWriter}) answers every lookup with those
     * two and never touches the caches, and one such mapper is created per route, so they are
     * {@code null} there.
     */
    @Nullable
    private final AtomicReferenceArray<TypeCache<ObjectReader>> cachedReaders;
    @Nullable
    private final AtomicReferenceArray<TypeCache<ObjectWriter>> cachedWriters;

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
        this.cachedReaders = new AtomicReferenceArray<>(TYPE_CACHE_SIZE);
        this.cachedWriters = new AtomicReferenceArray<>(TYPE_CACHE_SIZE);
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
        this.cachedReaders = null;
        this.cachedWriters = null;
    }

    private JacksonDatabindMapper(JacksonDatabindMapper from, ObjectReader reader, ObjectWriter writer) {
        this.objectMapper = from.objectMapper;
        this.config = from.config;
        this.treeCodec = from.treeCodec;
        this.specializedReader = reader;
        this.specializedWriter = writer;
        this.allowViews = from.allowViews;
        this.cachedReaders = null;
        this.cachedWriters = null;
    }

    private static ObjectMapper createDefaultMapper() {
        var objectMapperFactory = new ObjectMapperFactory();
        objectMapperFactory.setDeserializers(new JsonNodeDeserializer());
        objectMapperFactory.setSerializers(new JsonNodeSerializer());
        return objectMapperFactory.jsonMapperBuilder(null, null).build();
    }

    @Internal
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public JsonMapper createSpecific(Argument<?> type) {
        JacksonDatabindMapper jacksonDatabindMapper = new JacksonDatabindMapper(this, type, allowViews);
        if (allowViews) {
            Class<?> viewClass = type.getAnnotationMetadata().classValue(JsonView.class).orElse(null);
            if (viewClass != null) {
                return jacksonDatabindMapper.cloneWithViewClass(viewClass);
            }
        }
        return jacksonDatabindMapper;
    }

    /**
     * Get the {@link ObjectReader} for the given type. Readers are cached per type (see
     * {@link TypeCache}); package-private for tests.
     *
     * @param type The type to read
     * @return The reader
     */
    ObjectReader createReader(Argument<?> type) {
        if (specializedReader != null) {
            return specializedReader;
        }
        AtomicReferenceArray<TypeCache<ObjectReader>> cachedReaders = Objects.requireNonNull(this.cachedReaders);
        int typeHash = type.typeHashCode();
        int slot = slot(typeHash, null);
        TypeCache<ObjectReader> cached = cachedReaders.get(slot);
        if (cached != null && cached.type == type) {
            return cached.cachedValue;
        }
        Class<?> view = viewOf(type);
        if (view != null) {
            slot = slot(typeHash, view);
            cached = cachedReaders.get(slot);
        }
        if (cached != null && cached.matches(type, view)) {
            return cached.cachedValue;
        }
        ObjectReader reader = objectMapper.readerFor(JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
        if (view != null) {
            reader = reader.withView(view);
        }
        cachedReaders.set(slot, new TypeCache<>(type, view, reader));
        return reader;
    }

    /**
     * Get the {@link ObjectWriter} for the given type. Writers are cached per type (see
     * {@link TypeCache}); package-private for tests.
     *
     * @param type The type to write
     * @return The writer
     */
    ObjectWriter createWriter(Argument<?> type) {
        if (specializedWriter != null) {
            return specializedWriter;
        }
        AtomicReferenceArray<TypeCache<ObjectWriter>> cachedWriters = Objects.requireNonNull(this.cachedWriters);
        int typeHash = type.typeHashCode();
        int slot = slot(typeHash, null);
        TypeCache<ObjectWriter> cached = cachedWriters.get(slot);
        if (cached != null && cached.type == type) {
            return cached.cachedValue;
        }
        Class<?> view = viewOf(type);
        if (view != null) {
            slot = slot(typeHash, view);
            cached = cachedWriters.get(slot);
        }
        if (cached != null && cached.matches(type, view)) {
            return cached.cachedValue;
        }
        ObjectWriter writer = objectMapper.writerFor(JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
        if (view != null) {
            writer = writer.withView(view);
        }
        cachedWriters.set(slot, new TypeCache<>(type, view, writer));
        return writer;
    }

    @Nullable
    private static Class<?> viewOf(Argument<?> type) {
        return type.getAnnotationMetadata().classValue(JsonView.class).orElse(null);
    }

    /**
     * Cache slot for a type. Arguments without a view use the slot of the type hash alone, so
     * the identity check in the callers can look there before resolving the view; arguments with
     * a view use a slot derived from both, so a viewed and an unviewed argument of the same type
     * do not evict each other.
     */
    private static int slot(int typeHash, @Nullable Class<?> view) {
        int h = view == null ? typeHash : typeHash ^ (31 * view.hashCode());
        // spread the high bits, since typeHashCode of a simple class argument is 31 * (31 + identityHash)
        h ^= h >>> 16;
        return h & (TYPE_CACHE_SIZE - 1);
    }

    @Override
    @Nullable
    public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        return createReader(type).readValue(treeAsTokens(tree));
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        objectMapper.writeValue(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        createWriter(type).writeValue(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    @Nullable
    public <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        try {
            return createReader(type).readValue(inputStream);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    @Nullable
    public <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        try {
            return createReader(type).readValue(byteArray);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    @Nullable
    public <T> T readValue(ByteBuffer<?> byteBuffer, Argument<T> type) throws IOException {
        try (JsonParser parser = JacksonCoreParserFactory.createJsonParser((JsonFactory) objectMapper.tokenStreamFactory(), objectMapper._deserializationContext(), byteBuffer)) {
            return createReader(type).readValue(parser);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    @Nullable
    public <T> T readValue(ReadBuffer readBuffer, Argument<T> type) throws IOException {
        try {
            ObjectReader reader = createReader(type);
            Optional<T> direct = readBuffer.useFastHeapBuffer(bb -> Optional.ofNullable(reader.readValue(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining())));
            //noinspection OptionalAssignedToNull (intentional)
            if (direct != null) {
                return direct.orElse(null);
            }

            return reader.readValue(readBuffer.toInputStream());
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        if (specializedWriter != null) {
            specializedWriter.writeValue(outputStream, object);
        } else {
            objectMapper.writeValue(outputStream, object);
        }
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
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
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        return createWriter(type).writeValueAsBytes(object);
    }

    @Override
    public void updateValueFromTree(Object value, JsonNode tree) throws IOException {
        objectMapper.readerForUpdating(value).readValue(treeAsTokens(tree));
    }

    @Override
    public JsonMapper cloneWithFeatures(JsonFeatures features) {
        JacksonFeatures jacksonFeatures = (JacksonFeatures) features;

        MapperBuilder<?, ?> builder = objectMapper.rebuild();
        jacksonFeatures.getDeserializationFeatures().forEach(builder::configure);
        jacksonFeatures.getSerializationFeatures().forEach(builder::configure);
        for (Class<? extends JacksonModule> moduleClass : jacksonFeatures.getAdditionalModules()) {
            builder.addModule(InstantiationUtils.instantiate(moduleClass));
        }

        return new JacksonDatabindMapper(builder.build(), allowViews);
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        ObjectReader reader = objectMapper.readerWithView(viewClass);
        ObjectWriter writer = objectMapper.writerWithView(viewClass);

        return new JacksonDatabindMapper(this, reader, writer);
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return config;
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new JacksonCoreProcessor(streamArray, objectMapper.tokenStreamFactory(), config) {
            @Override
            public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                onSubscribe.accept(this);
                super.subscribe(downstreamSubscriber);
            }
        };
    }

    @Override
    public Optional<JsonFeatures> detectFeatures(AnnotationMetadata annotations) {
        return Optional.ofNullable(annotations.getAnnotation(io.micronaut.jackson.annotation.JacksonFeatures.class))
            .map(JacksonFeatures::fromAnnotation);
    }

    private JsonParser treeAsTokens(JsonNode tree) {
        DeserializationContext context = objectMapper._deserializationContext(); // Not supposed to be used technically
        return treeCodec.treeAsTokens(tree, context);
    }

    /**
     * One entry of the per-type reader or writer cache. The reader or writer for a type depends
     * on the type (including its type arguments) and on the {@link JsonView} class on the
     * argument, so an entry matches an argument that has the same type and the same view; the
     * {@link Argument} instance need not be the same, which matters for arguments created per
     * request with {@link Argument#ofInstance(Object)}.
     *
     * <p>The caches are fixed-size arrays indexed by type hash. A type whose slot is taken by
     * another type simply replaces that entry, so the cache never grows past its size and a
     * lookup never allocates.
     *
     * @param type        The argument the value was created for
     * @param view        The {@link JsonView} class of {@code type}, or {@code null}
     * @param cachedValue The reader or writer
     * @param <T>         The value type
     */
    private record TypeCache<T>(Argument<?> type, @Nullable Class<?> view, T cachedValue) {
        boolean matches(Argument<?> type, @Nullable Class<?> view) {
            return this.view == view && this.type.equalsType(type);
        }
    }
}
