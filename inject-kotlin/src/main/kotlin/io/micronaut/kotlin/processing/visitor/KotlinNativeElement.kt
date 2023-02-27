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
package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import io.micronaut.core.annotation.Internal
import io.micronaut.core.reflect.ReflectionUtils.findMethod
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement

@Internal
open class KotlinNativeElement(
    val element: KSAnnotated,
    val owner: KotlinNativeElement?,
    internal val kotlinNativeType: Any
) {

    constructor(element: KSAnnotated) : this(element, null, resolveKotlinNativeType(element))

    constructor(element: KSAnnotated, owner: KotlinNativeElement? = null) : this(
        element,
        owner,
        resolveKotlinNativeType(element)
    )

    companion object Helper {
        fun resolveKotlinNativeType(nativeType: Any): Any {

            val kind: String = when (nativeType) {
                is KSClassDeclaration -> "ClassOrObject"
                is KSValueParameter -> "Parameter"
                is KSPropertyDeclaration -> "Property"
                is KSPropertySetter -> "PropertySetter"
                is KSPropertyGetter -> "PropertyGetter"
                is KSFunctionDeclaration -> "Function"
                is KSTypeArgument -> "TypeArgument"
                is KSTypeParameter -> "TypeParameter"
                else -> throw IllegalStateException("Unknown native type ${nativeType.javaClass}")
            }

            val javaClass = nativeType.javaClass
            val method = findMethod(javaClass, "getKt$kind")
                .orElseGet {
                    findMethod(javaClass, "getPsi").orElseGet {
                        findMethod(javaClass, "getDescriptor").orElse(null)
                    }
                }

            return if (method != null && method.canAccess(nativeType)) {
                method.invoke(nativeType)
            } else {
                nativeType
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KotlinNativeElement) return false
        if (kotlinNativeType != other.kotlinNativeType) return false
        if (owner != other.owner) return false
        return true
    }

    override fun hashCode(): Int {
        return kotlinNativeType.hashCode()
    }

}

internal class KotlinClassNativeElement(
    val declaration: KSClassDeclaration,
    val type: KSType? = null,
    owner: KotlinNativeElement? = null
) : KotlinNativeElement(declaration, owner) {

    init {
        if (type != null && owner == null) {
            throw IllegalStateException("Missing")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KotlinClassNativeElement) return false
        if (kotlinNativeType != other.kotlinNativeType) return false
        if (owner != other.owner) return false
        if (type != other.type) return false
        return true
    }

    override fun hashCode(): Int {
        return kotlinNativeType.hashCode()
    }

    override fun toString(): String {
        return "KotlinClassNativeElement(declaration=$declaration, type=$type)"
    }

}

class KotlinMethodNativeElement(
    val declaration: KSFunctionDeclaration,
) : KotlinNativeElement(declaration) {

    override fun toString(): String {
        return "KotlinMethodNativeElement(declaration=$declaration)"
    }
}

class KotlinFieldNativeElement(
    val declaration: KSPropertyDeclaration,
) : KotlinNativeElement(declaration) {

    override fun toString(): String {
        return "KotlinFieldNativeElement(declaration=$declaration)"
    }
}

class KotlinMethodParameterNativeElement(
    val declaration: KSValueParameter,
    val method: KotlinNativeElement
) : KotlinNativeElement(declaration, method) {

    override fun toString(): String {
        return "KotlinMethodParameterNativeElement(declaration=$declaration, method=$method)"
    }
}

class KotlinPropertySetterNativeElement(
    val declaration: KSPropertySetter,
) : KotlinNativeElement(declaration) {

    override fun toString(): String {
        return "KotlinPropertySetterNativeElement(declaration=$declaration)"
    }
}

class KotlinPropertyGetterNativeElement(
    val declaration: KSPropertyGetter,
) : KotlinNativeElement(declaration) {

    override fun toString(): String {
        return "KotlinPropertyGetterNativeElement(declaration=$declaration)"
    }
}

class KotlinTypeParameterNativeElement(
    val declaration: KSTypeParameter,
    owner: KotlinNativeElement
) : KotlinNativeElement(declaration, owner) {

    override fun toString(): String {
        return "KotlinTypeParameterNativeElement(declaration=$declaration)"
    }
}

class KotlinTypeArgumentNativeElement(
    val declaration: KSTypeArgument,
    owner: KotlinNativeElement
) : KotlinNativeElement(declaration, owner) {

    override fun toString(): String {
        return "KotlinTypeArgumentNativeElement(declaration=$declaration)"
    }
}

class KotlinPropertyNativeElement(
    val declaration: KSPropertyDeclaration
) : KotlinNativeElement(declaration) {

    override fun toString(): String {
        return "KotlinPropertyNativeElement(declaration=$declaration)"
    }
}

class KotlinSimplePropertyNativeElement(
    val declaration: KSAnnotated
) : KotlinNativeElement(declaration) {

    constructor(
        type: ClassElement,
        field: FieldElement?,
        getter: MethodElement?,
        setter: MethodElement?
    ) : this(pickDeclaration(type, field, getter, setter))


    companion object Helper {
        private fun pickDeclaration(
            type: ClassElement,
            field: FieldElement?,
            getter: MethodElement?,
            setter: MethodElement?
        ): KSAnnotated {
            return when {
                field is AbstractKotlinElement<*> -> {
                    field.getNativeType().element
                }

                getter is AbstractKotlinElement<*> -> {
                    getter.getNativeType().element
                }

                setter is AbstractKotlinElement<*> -> {
                    setter.getNativeType().element
                }

                else -> {
                    (type as AbstractKotlinElement<*>).nativeType.element
                }
            }
        }
    }

    override fun toString(): String {
        return "KotlinSimplePropertyNativeElement(declaration=$declaration)"
    }
}


