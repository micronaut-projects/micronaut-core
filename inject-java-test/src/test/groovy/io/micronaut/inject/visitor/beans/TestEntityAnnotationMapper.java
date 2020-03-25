/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class TestEntityAnnotationMapper implements NamedAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Entity";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<Introspected> builder = AnnotationValue.builder(Introspected.class)
                // don't bother with transients properties
                .member("excludedAnnotations", "javax.persistence.Transient")
                // following are indexed for fast lookups
                .member("indexed",
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.Id").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.Version").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.GeneratedValue").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.Basic").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.Embedded").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.OneToMany").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.OneToOne").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.ManyToOne").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.ElementCollection").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.Enumerated").build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", "javax.persistence.Column")
                                .member("member", "name").build()
                );
        return Collections.singletonList(
                builder.build()
        );
    }
}
