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
package io.micronaut.kotlin.processing

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import io.micronaut.kotlin.processing.visitor.KSFunctionReference
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.lang.StringBuilder

@OptIn(KspExperimental::class)
fun KSDeclaration.getBinaryName(resolver: Resolver): String {
    val binaryName = resolver.mapKotlinNameToJava(this.qualifiedName!!)?.asString()
    return if (binaryName != null) {
        binaryName
    } else {
        val className = StringBuilder(packageName.asString())
        val hierarchy = mutableListOf(this)
        var parentDeclaration = parentDeclaration
        while (parentDeclaration is KSClassDeclaration) {
            hierarchy.add(0, parentDeclaration)
            parentDeclaration = parentDeclaration.parentDeclaration
        }
        hierarchy.joinTo(className, "$", ".")
        className.toString()
    }
}

fun KSPropertyDeclaration.isTypeReference(): Boolean {
    var property: KSPropertyDeclaration? = this
    while (property != null) {
        if (property.type.resolve().declaration is KSTypeParameter) {
            return true
        }
        property = property.findOverridee()
    }
    return false
}

fun KSPropertySetter.getVisibility(): Visibility {
    return when {
        this.modifiers.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
        this.modifiers.contains(Modifier.PRIVATE) -> Visibility.PRIVATE
        this.modifiers.contains(Modifier.PROTECTED) ||
                this.modifiers.contains(Modifier.OVERRIDE) -> Visibility.PROTECTED
        this.modifiers.contains(Modifier.INTERNAL) -> Visibility.INTERNAL
        else -> if (this.origin != Origin.JAVA && this.origin != Origin.JAVA_LIB)
            Visibility.PUBLIC else Visibility.JAVA_PACKAGE
    }
}

@OptIn(KspExperimental::class)
fun KSAnnotated.getClassDeclaration(visitorContext: KotlinVisitorContext) : KSClassDeclaration {
    when (this) {
        is KSType -> {
            return this.declaration.getClassDeclaration(visitorContext)
        }
        is KSClassDeclaration -> {
            return this
        }
        is KSTypeReference -> {
            return this.resolve().declaration.getClassDeclaration(visitorContext)
        }
        is KSTypeParameter -> {
            return resolveDeclaration(
                this.bounds.firstOrNull()?.resolve()?.declaration,
                visitorContext
            )
        }
        is KSTypeArgument -> {
            return resolveDeclaration(this.type?.resolve()?.declaration, visitorContext)
        }
        is KSTypeAlias -> {
            val declaration = this.type.resolve().declaration
            return declaration.getClassDeclaration(visitorContext)
        }
        else -> {
            return visitorContext.resolver.getJavaClassByName(Object::class.java.name)!!
        }
    }
}

@OptIn(KspExperimental::class)
private fun resolveDeclaration(
    declaration: KSDeclaration?,
    visitorContext: KotlinVisitorContext
): KSClassDeclaration {
    return if (declaration is KSClassDeclaration) {
        declaration
    } else {
        visitorContext.resolver.getJavaClassByName(Object::class.java.name)!!
    }
}
