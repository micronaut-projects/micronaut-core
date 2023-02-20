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
import com.google.devtools.ksp.symbol.KSType
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import java.util.*

class KotlinEnumElement(private val type: KSType, elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory, visitorContext: KotlinVisitorContext):
    KotlinClassElement(type, elementAnnotationMetadataFactory, visitorContext), EnumElement {

    override fun values(): List<String> {
        return classDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .map { decl -> decl.simpleName.asString() }
            .toList()
    }

    override fun getDefaultConstructor(): Optional<MethodElement> {
        return Optional.empty()
    }

    override fun copyThis(): KotlinEnumElement {
        return KotlinEnumElement(
            type,
            annotationMetadataFactory,
            visitorContext
        )
    }

    override fun getPrimaryConstructor(): Optional<MethodElement> {
        return Optional.of(KotlinEnumConstructorElement(this))
    }
}
