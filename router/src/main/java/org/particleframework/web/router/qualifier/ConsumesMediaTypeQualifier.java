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
package org.particleframework.web.router.qualifier;

import org.particleframework.context.Qualifier;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.MediaType;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.http.annotation.Consumes;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link org.particleframework.context.annotation.Bean} {@link Qualifier} that qualifies based on the
 * value of the media type defined in the {@link Consumes} annotation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConsumesMediaTypeQualifier<T> implements Qualifier<T> {
    private final MediaType contentType;

    public ConsumesMediaTypeQualifier(MediaType contentType) {
        this.contentType = contentType;
    }

    @Override
    public Stream<BeanDefinition<T>> reduce(Class<T> beanType, Stream<BeanDefinition<T>> candidates) {
        return candidates.filter(candidate -> {
                    Consumes consumes = candidate.getAnnotation(Consumes.class);
                    if (consumes != null) {
                        Set<String> consumedTypes = Arrays.stream(consumes.value()).map(MediaType::new).map(MediaType::getExtension).collect(Collectors.toSet());
                        return consumedTypes.contains(contentType.getExtension());
                    }
                    return false;
                }
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConsumesMediaTypeQualifier that = (ConsumesMediaTypeQualifier) o;

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
