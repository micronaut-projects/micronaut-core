/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.kotlin.processing.visitor

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

/**
 * Kotlin implementation of [io.micronaut.inject.ast.AnnotationElement].
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
internal class KotlinAnnotationElement(
    private val annotationNativeType: KotlinClassNativeElement,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : KotlinClassElement(
    annotationNativeType,
    elementAnnotationMetadataFactory,
    null,
    visitorContext
), AnnotationElement {

    // an annotation type cannot be generic, so the type arguments can be ignored
    override fun copyThis() = KotlinAnnotationElement(
        annotationNativeType,
        elementAnnotationMetadataFactory,
        visitorContext
    )

    override fun isInherited() = declaration.annotations.any { annotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == AnnotationUtil.ANN_INHERITED
    }
}
