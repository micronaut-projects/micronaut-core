/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.web.router.qualifier;

import io.micronaut.context.Qualifier;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.BeanType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link io.micronaut.context.annotation.Bean} {@link Qualifier} that qualifies based on the value of the media type
 * defined in the {@link Produces} annotation.
 *
 * @param <T> The Type
 * @author Sergio del Amo
 * @since 1.0
 */
public class ProducesMediaTypeQualifier<T> implements Qualifier<T> {

    private final MediaType contentType;

    /**
     * @param contentType The content type as {@link MediaType}
     */
    public ProducesMediaTypeQualifier(MediaType contentType) {
        this.contentType = contentType;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> {
            MediaType[] consumes = MediaType.of(candidate.getAnnotationMetadata().stringValues(Produces.class));
            if (ArrayUtils.isNotEmpty(consumes)) {
                Set<String> consumedTypes = Arrays.stream(consumes).map(MediaType::getExtension).collect(Collectors.toSet());
                return consumedTypes.contains(contentType.getExtension());
            }
            return false;
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

        ProducesMediaTypeQualifier that = (ProducesMediaTypeQualifier) o;

        return contentType.equals(that.contentType);
    }

    @Override
    public int hashCode() {
        return contentType.hashCode();
    }

    @Override
    public String toString() {
        return HttpHeaders.CONTENT_TYPE + ": " + contentType;
    }
}
