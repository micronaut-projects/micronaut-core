/*
 * Copyright 2017-2022 original authors
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

import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import java.util.*

internal class KotlinEnumElement(
    nativeType: KotlinClassNativeElement,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
    resolvedTypeArguments: Map<String, ClassElement>?
) : KotlinClassElement(
    nativeType,
    elementAnnotationMetadataFactory,
    resolvedTypeArguments,
    visitorContext
), EnumElement {

    override val asType: KotlinClassElement by lazy {
        this
    }

    override fun values() = declaration.declarations
        .filterIsInstance<KSClassDeclaration>()
        .map { ksClassDeclaration -> ksClassDeclaration.simpleName.asString() }
        .toList()

    override fun elements() = declaration.declarations
        .filterIsInstance<KSClassDeclaration>()
        .map { ksClassDeclaration ->
            KotlinEnumConstantElement(
                this,
                ksClassDeclaration,
                elementAnnotationMetadataFactory,
                visitorContext
            )
        }
        .toList()

    override fun getDefaultConstructor(): Optional<MethodElement> = Optional.empty()

    override fun getPrimaryConstructor(): Optional<MethodElement> {
        val primaryConstructor = super<KotlinClassElement>.getPrimaryConstructor()
        if (primaryConstructor.isPresent) {
            return primaryConstructor
        }
        return Optional.of(KotlinEnumConstructorElement(this))
    }

    override fun copyThis() = KotlinEnumElement(
        nativeType,
        elementAnnotationMetadataFactory,
        visitorContext,
        resolvedTypeArguments
    )
}
