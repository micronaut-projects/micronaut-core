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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.jackson.codec.JacksonFeatures;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.jackson.core.tree.TreeGenerator;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonFeatures;
import io.micronaut.jackson.core.parser.JacksonCoreProcessor;
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

    @Inject
    @Internal
    public JacksonDatabindMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.config = JsonStreamConfig.DEFAULT
                .withUseBigDecimalForFloats(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS))
                .withUseBigIntegerForInts(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS));
        this.treeCodec = JsonNodeTreeCodec.getInstance().withConfig(config);
    }

    @Internal
    public JacksonDatabindMapper() {
        this(new ObjectMapper());
    }

    @Internal
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException {
        return objectMapper.readValue(treeCodec.treeAsTokens(tree), JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
    }

    @Override
    public @NonNull JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        treeGenerator.setCodec(objectMapper);
        objectMapper.writeValue(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException {
        return objectMapper.readValue(inputStream, JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
    }

    @Override
    public <T> T readValue(@NonNull byte[] byteArray, @NonNull Argument<T> type) throws IOException {
        return objectMapper.readValue(byteArray, JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
    }

    @Override
    public void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException {
        objectMapper.writeValue(outputStream, object);
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        return objectMapper.writeValueAsBytes(object);
    }

    @Override
    public void updateValueFromTree(Object value, @NonNull JsonNode tree) throws IOException {
        objectMapper.readerForUpdating(value).readValue(treeCodec.treeAsTokens(tree));
    }

    @Override
    public @NonNull JsonMapper cloneWithFeatures(@NonNull JsonFeatures features) {
        JacksonFeatures jacksonFeatures = (JacksonFeatures) features;

        ObjectMapper objectMapper = this.objectMapper.copy();
        jacksonFeatures.getDeserializationFeatures().forEach(objectMapper::configure);
        jacksonFeatures.getSerializationFeatures().forEach(objectMapper::configure);

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
    public @NonNull Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
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
}
