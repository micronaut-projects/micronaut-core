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
package io.micronaut.core.io.scan;

import java.util.Objects;
import java.util.stream.Stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.StringUtils;

/**
 * An implementation of {@link io.micronaut.core.io.scan.AnnotationScanner} that scans available {@link io.micronaut.core.beans.BeanIntrospection}
 * instances through the {@link io.micronaut.core.beans.BeanIntrospector} API.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public class BeanIntrospectionScanner implements AnnotationScanner {
    @Override
    public @NonNull Stream<Class<?>> scan(@NonNull String annotation, @NonNull String pkg) {
        Objects.requireNonNull(annotation, "Annotation type cannot be null");
        Objects.requireNonNull(pkg, "Package to scan cannot be null");

        if (StringUtils.isNotEmpty(pkg)) {
            final String prefix = pkg + ".";
            return BeanIntrospector.SHARED
                    .findIntrospectedTypes(ref ->
                            ref.getAnnotationMetadata().hasStereotype(annotation) &&
                            ref.isPresent() &&
                            ref.getBeanType().getName().startsWith(prefix)
                    ).stream();
        } else {
            return Stream.empty();
        }
    }
}
