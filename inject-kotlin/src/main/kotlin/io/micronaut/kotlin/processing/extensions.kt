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
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import org.objectweb.asm.Type

@OptIn(KspExperimental::class)
internal fun KSFunctionDeclaration.getBinaryName(resolver: Resolver): String {
    return resolver.getJvmName(this)!!
}

@OptIn(KspExperimental::class)
internal fun KSPropertyAccessor.getBinaryName(resolver: Resolver): String {
    return resolver.getJvmName(this)!!
}


@OptIn(KspExperimental::class)
internal fun KSDeclaration.getBinaryName(resolver: Resolver, visitorContext: KotlinVisitorContext): String {
    var declaration = this
    if (declaration is KSFunctionDeclaration) {
        val parent = declaration.parentDeclaration
        if (parent != null) {
            declaration = parent
        }
    }
    val binaryName = if (declaration.qualifiedName != null) resolver.mapKotlinNameToJava(declaration.qualifiedName!!)?.asString() else null
    if (binaryName != null) {
        return binaryName
    }
    if (declaration.qualifiedName == null) {
        return "java.lang.Object" // Anonymous
    }
    val classDeclaration = declaration.getClassDeclaration(visitorContext)
    val qn = classDeclaration.qualifiedName
    if (qn != null) {
        val asString = resolver.mapKotlinNameToJava(qn)?.asString()
        if (asString != null) {
            return asString
        }
    }
    if (declaration is KSClassDeclaration) {
        val qualifiedName = declaration.qualifiedName
        if (qualifiedName != null && qualifiedName.asString() == "kotlin.Unit") {
            return "kotlin.Unit"
        }
        val signature = resolver.mapToJvmSignature(declaration)
        if (signature != null) {
            return Type.getType(signature).className
        }
    }
    if (declaration is KSTypeAlias) {
        return declaration.name.asString()
    }
    return if (declaration.origin != Origin.SYNTHETIC) {
        computeName(declaration)
    } else {
        declaration.simpleName.asString()
    }
}

private fun computeName(declaration: KSDeclaration): String {
    val className = StringBuilder(declaration.packageName.asString())
    val hierarchy = mutableListOf(declaration)
    var parentDeclaration = declaration.parentDeclaration
    while (parentDeclaration is KSClassDeclaration) {
        hierarchy.add(0, parentDeclaration)
        parentDeclaration = parentDeclaration.parentDeclaration
    }
    hierarchy.joinTo(className, "$", ".")
    return className.toString()
}

internal fun KSPropertySetter.getVisibility(): Visibility {
    val modifierSet = try {
        this.modifiers
    } catch (e: IllegalStateException) {
        // KSP bug: IllegalStateException: unhandled visibility: invisible_fake
        setOf(Modifier.INTERNAL)
    }
    return when {
        modifierSet.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
        modifierSet.contains(Modifier.PRIVATE) -> Visibility.PRIVATE
        modifierSet.contains(Modifier.PROTECTED) ||
                modifierSet.contains(Modifier.OVERRIDE) -> Visibility.PROTECTED
        modifierSet.contains(Modifier.INTERNAL) -> Visibility.INTERNAL
        else -> if (this.origin != Origin.JAVA && this.origin != Origin.JAVA_LIB)
            Visibility.PUBLIC else Visibility.JAVA_PACKAGE
    }
}

@OptIn(KspExperimental::class)
internal fun KSAnnotated.getClassDeclaration(visitorContext: KotlinVisitorContext) : KSClassDeclaration {
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
        is KSValueParameter -> {
            val p = this.parent
            if (p is KSDeclaration) {
                return p.getClassDeclaration(visitorContext)
            } else {
                return visitorContext.resolver.getJavaClassByName(Object::class.java.name)!!
            }
        }
        is KSFunctionDeclaration -> {
            val parentDeclaration = this.parentDeclaration
            if (parentDeclaration != null) {
                return parentDeclaration.getClassDeclaration(visitorContext)
            }
            return visitorContext.resolver.getJavaClassByName(Object::class.java.name)!!
        }
        is KSPropertyDeclaration -> {
            val parentDeclaration = this.parentDeclaration
            if (parentDeclaration != null) {
                return parentDeclaration.getClassDeclaration(visitorContext)
            }
            return visitorContext.resolver.getJavaClassByName(Object::class.java.name)!!
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
