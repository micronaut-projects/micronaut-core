/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.beans.visitor.persistence;

import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes each use of Jakarta Persistence's Access annotation also represent an {@link Introspected.Property}.
 *
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
public final class JakartaAccessAnnotationMapper implements NamedAnnotationMapper {

    @Override
    public String getName() {
        return "jakarta.persistence.Access";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        List<BeanProperties.AccessKind> accessKinds = List.of(annotation.enumValues(BeanProperties.AccessKind.class));
        return List.of(AnnotationValue.builder(Introspected.Property.class)
            .member("ignoreOtherAccessors", accessKinds.contains(BeanProperties.AccessKind.FIELD))
            .build());
    }
}
