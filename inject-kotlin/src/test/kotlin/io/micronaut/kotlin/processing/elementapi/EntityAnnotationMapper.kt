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
package io.micronaut.kotlin.processing.elementapi

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.annotation.NamedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext

class EntityAnnotationMapper : NamedAnnotationMapper {
    @NonNull
    override fun getName(): String {
        return "javax.persistence.Entity"
    }

    override fun map(
        annotation: AnnotationValue<Annotation>,
        visitorContext: VisitorContext
    ): List<AnnotationValue<*>> {
        val builder = AnnotationValue.builder(
            Introspected::class.java
        )
            .member( // following are indexed for fast lookups
                "indexed",
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.Id").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.Version").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.GeneratedValue").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.Basic").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.Embedded").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.OneToMany").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.OneToOne").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.ManyToOne").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.ElementCollection").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.Enumerated").build(),
                AnnotationValue.builder(
                    Introspected.IndexedAnnotation::class.java
                )
                    .member("annotation", "javax.persistence.Column")
                    .member("member", "name").build()
            )
        return listOf<AnnotationValue<*>>(
            builder.build()
        )
    }
}
