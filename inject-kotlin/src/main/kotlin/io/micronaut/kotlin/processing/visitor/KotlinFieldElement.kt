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

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement

class KotlinFieldElement(declaration: KSPropertyDeclaration,
                         private val declaringType: ClassElement,
                         annotationMetadata: AnnotationMetadata,
                         visitorContext: KotlinVisitorContext
) : AbstractKotlinElement<KSPropertyDeclaration>(declaration, annotationMetadata, visitorContext), FieldElement {

    override fun getName(): String {
        return declaration.simpleName.asString()
    }

    override fun getType(): ClassElement {
       return visitorContext.elementFactory.newClassElement(declaration.type.resolve())
    }

    override fun isPrivate(): Boolean = true

    override fun getDeclaringType() = declaringType
}
