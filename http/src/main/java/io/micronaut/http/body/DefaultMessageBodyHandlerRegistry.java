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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.qualifiers.FilteringQualifier;
import io.micronaut.inject.qualifiers.MatchArgumentQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stores message body readers and writers.
 *
 * @author Graeme Rocher
 * @since 4.0.0
 */
@SuppressWarnings("unused")
@Experimental
@Singleton
@BootstrapContextCompatible
public final class DefaultMessageBodyHandlerRegistry extends RawMessageBodyHandlerRegistry {
    private final BeanContext beanLocator;
    private final List<CodecConfiguration> codecConfigurations;

    /**
     * Default constructor.
     *
     * @param beanLocator         The bean locator.
     * @param codecConfigurations The codec configurations
     * @param rawHandlers         The handlers for raw types
     */
    DefaultMessageBodyHandlerRegistry(BeanContext beanLocator,
                                      List<CodecConfiguration> codecConfigurations,
                                      List<RawMessageBodyHandler<?>> rawHandlers) {
        super(rawHandlers);
        this.beanLocator = beanLocator;
        this.codecConfigurations = codecConfigurations;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    protected <T> MessageBodyReader<T> findReaderImpl(Argument<T> type, List<MediaType> mediaTypes) {
        return beanLocator.getBeansOfType(
                Argument.of(MessageBodyReader.class), // Select all readers and eliminate by the type later
                Qualifiers.byQualifiers(
                    // Filter by media types first before filtering by the type hierarchy
                    newMediaTypeQualifier(Argument.of(MessageBodyReader.class, type), mediaTypes, Consumes.class),
                    MatchArgumentQualifier.covariant(MessageBodyReader.class, type)
                )
            ).stream()
            .filter(reader -> mediaTypes.stream().anyMatch(mediaType -> reader.isReadable(type, mediaType)))
            .findFirst().orElse(null);
    }

    @NonNull
    private <T, B> MediaTypeQualifier<B> newMediaTypeQualifier(Argument<T> type, List<MediaType> mediaTypes, Class<? extends Annotation> qualifierType) {
        List<MediaType> resolvedMediaTypes = resolveMediaTypes(mediaTypes);
        return new MediaTypeQualifier<>(type, resolvedMediaTypes, qualifierType);
    }

    @NonNull
    private List<MediaType> resolveMediaTypes(List<MediaType> mediaTypes) {
        if (codecConfigurations.isEmpty()) {
            return mediaTypes;
        }
        List<MediaType> resolvedMediaTypes = new ArrayList<>(mediaTypes.size());
        resolvedMediaTypes.addAll(mediaTypes);
        for (MediaType mediaType : mediaTypes) {
            for (CodecConfiguration codecConfiguration : codecConfigurations) {
                List<MediaType> additionalTypes = codecConfiguration.getAdditionalTypes();
                if (additionalTypes.contains(mediaType)) {
                    beanLocator.findBean(MediaTypeCodec.class, Qualifiers.byName(codecConfiguration.getName())).ifPresent(codec ->
                        resolvedMediaTypes.addAll(codec.getMediaTypes())
                    );
                    break;
                }
            }
        }
        return resolvedMediaTypes;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    protected <T> MessageBodyWriter<T> findWriterImpl(Argument<T> type, List<MediaType> mediaTypes) {
        return beanLocator.getBeansOfType(
                Argument.of(MessageBodyWriter.class), // Select all writers and eliminate by the type later
                Qualifiers.byQualifiers(
                    // Filter by media types first before filtering by the type hierarchy
                    newMediaTypeQualifier(Argument.of(MessageBodyWriter.class, type), mediaTypes, Produces.class),
                    MatchArgumentQualifier.contravariant(MessageBodyWriter.class, type)
                )
            ).stream()
            .filter(writer -> mediaTypes.stream().anyMatch(mediaType -> writer.isWriteable(type, mediaType)))
            .findFirst().orElse(null);
    }

    private static final class MediaTypeQualifier<T> extends FilteringQualifier<T> {
        private final Argument<?> type;
        private final List<MediaType> mediaTypes;
        private final Class<? extends Annotation> annotationType;

        private MediaTypeQualifier(Argument<?> type,
                                   List<MediaType> mediaTypes,
                                   Class<? extends Annotation> annotationType) {
            this.type = type;
            this.mediaTypes = mediaTypes;
            this.annotationType = annotationType;
        }

        @Override
        public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
            if (type.getType() == MessageBodyWriter.class && candidate instanceof BeanDefinition<?> definition) {
                List<Argument<?>> consumedType = definition.getTypeArguments(MessageBodyWriter.class);
                Argument<?>[] typeParameters = type.getTypeParameters();
                if (ArrayUtils.isEmpty(typeParameters)) {
                    return false;
                }

                Argument<?> requiredType = typeParameters[0];
                if (consumedType.isEmpty() || isInvalidType(consumedType, requiredType)) {
                    return false;
                }
            }
            String[] applicableTypes = candidate.getAnnotationMetadata().stringValues(annotationType);
            if (applicableTypes.length == 0) {
                return true;
            }
            for (String mt : applicableTypes) {
                if (mediaTypes.contains(new MediaType(mt))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isInvalidType(List<Argument<?>> consumedType, Argument<?> requiredType) {
            Argument<?> argument = consumedType.get(0);
            return !(argument.isTypeVariable() || argument.isAssignableFrom(requiredType.getType()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MediaTypeQualifier<?> that = (MediaTypeQualifier<?>) o;
            return type.equalsType(that.type) && mediaTypes.equals(that.mediaTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type.typeHashCode(), mediaTypes);
        }

        @Override
        public String toString() {
            return "MediaTypeQualifier[" +
                "type=" + type + ", " +
                "mediaTypes=" + mediaTypes + ", " +
                "annotationType=" + annotationType + ']';
        }

    }
}
