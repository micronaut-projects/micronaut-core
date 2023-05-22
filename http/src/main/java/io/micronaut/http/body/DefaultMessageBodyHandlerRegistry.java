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
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
     * @param beanLocator The bean locator.
     * @param codecConfigurations The codec configurations
     * @param rawHandlers The handlers for raw types
     */
    DefaultMessageBodyHandlerRegistry(
        BeanContext beanLocator,
        List<CodecConfiguration> codecConfigurations,
        List<RawMessageBodyHandler<?>> rawHandlers) {
        super(rawHandlers);
        this.beanLocator = beanLocator;
        this.codecConfigurations = codecConfigurations;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected <T> MessageBodyReader<T> findReaderImpl(Argument<T> type, List<MediaType> mediaTypes) {
        Collection<BeanDefinition<MessageBodyReader>> beanDefinitions = beanLocator.getBeanDefinitions(
            Argument.of(MessageBodyReader.class, type),
            newMediaTypeQualifier(type, mediaTypes, Consumes.class)
        );
        if (beanDefinitions.size() == 1) {
            return beanLocator.getBean(beanDefinitions.iterator().next());
        } else {
            List<BeanDefinition<MessageBodyReader>> exactMatch = beanDefinitions.stream()
                .filter(d -> {
                    List<Argument<?>> typeArguments = d.getTypeArguments(MessageBodyReader.class);
                    if (typeArguments.isEmpty()) {
                        return false;
                    } else {
                        return type.equalsType(typeArguments.get(0));
                    }
                }).toList();
            if (exactMatch.size() == 1) {
                return beanLocator.getBean(exactMatch.iterator().next());
            } else {
                // pick highest priority
                return OrderUtil.sort(beanDefinitions.stream())
                    .findFirst()
                    .map(beanLocator::getBean)
                    .orElse(null);
            }
        }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected <T> MessageBodyWriter<T> findWriterImpl(Argument<T> type, List<MediaType> mediaTypes) {
        Collection<BeanDefinition<MessageBodyWriter>> beanDefinitions = beanLocator.getBeanDefinitions(
            Argument.of(MessageBodyWriter.class, type),
            newMediaTypeQualifier(type, mediaTypes, Produces.class)
        );
        if (beanDefinitions.size() == 1) {
            return beanLocator.getBean(beanDefinitions.iterator().next());
        } else {
            List<BeanDefinition<MessageBodyWriter>> exactMatch = beanDefinitions.stream()
                .filter(d -> {
                    List<Argument<?>> typeArguments = d.getTypeArguments(MessageBodyWriter.class);
                    if (typeArguments.isEmpty()) {
                        return false;
                    } else {
                        return type.equalsType(typeArguments.get(0));
                    }
                }).toList();
            if (exactMatch.size() == 1) {
                return beanLocator.getBean(exactMatch.iterator().next());
            } else {
                // pick highest priority
                return OrderUtil.sort(beanDefinitions.stream())
                    .findFirst()
                    .map(beanLocator::getBean)
                    .orElse(null);
            }
        }
    }

    private record MediaTypeQualifier<T>(Argument<?> type,
                                         List<MediaType> mediaTypes,
                                         Class<? extends Annotation> annotationType) implements Qualifier<T> {

        @Override
        public <B extends BeanType<T>> Stream<B> reduce(Class<T> beanType, Stream<B> candidates) {
            return candidates.filter(c -> {
                String[] applicableTypes = c.getAnnotationMetadata().stringValues(annotationType);
                return ((applicableTypes.length == 0) || Arrays.stream(applicableTypes)
                    .anyMatch(mt -> mediaTypes.contains(new MediaType(mt)))
                );
            });
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
    }
}
