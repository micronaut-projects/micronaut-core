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

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import java.util.*

@OptIn(KspExperimental::class)
internal abstract class AbstractKotlinPropertyAccessorMethodElement<T : KotlinNativeElement>(
    nativeType: T,
    private val accessor: KSPropertyAccessor,
    private val visibility: Visibility,
    owningType: ClassElement,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
) : AbstractKotlinMethodElement<T>(
    nativeType,
    visitorContext.resolver.getJvmName(accessor)!!,
    owningType,
    elementAnnotationMetadataFactory,
    visitorContext
), MethodElement {

    override val internalDeclaringType: ClassElement by lazy {
        resolveDeclaringType(accessor.receiver, owningType)
    }

    override val internalDeclaredTypeArguments: Map<String, ClassElement> = emptyMap()

    override fun isSynthetic() = true

    override fun isFinal() = !accessor.receiver.isOpen()

    override fun isAbstract(): Boolean = accessor.receiver.isAbstract()

    override fun isPublic() = visibility == Visibility.PUBLIC

    override fun isProtected() = visibility == Visibility.PROTECTED

    override fun isPrivate() = visibility == Visibility.PRIVATE

    override fun hides(memberElement: MemberElement?) =
        // not sure how to implement this correctly for Kotlin
        false

}
