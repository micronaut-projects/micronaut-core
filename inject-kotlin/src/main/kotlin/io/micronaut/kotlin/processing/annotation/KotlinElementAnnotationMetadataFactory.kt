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
package io.micronaut.kotlin.processing.annotation

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

class KotlinElementAnnotationMetadataFactory(
    isReadOnly: Boolean,
    metadataBuilder: KotlinAnnotationMetadataBuilder
) : AbstractElementAnnotationMetadataFactory<KSAnnotated, KSAnnotation>(isReadOnly, metadataBuilder) {
    override fun readOnly(): ElementAnnotationMetadataFactory {
        return KotlinElementAnnotationMetadataFactory(true, metadataBuilder as KotlinAnnotationMetadataBuilder)
    }
}
