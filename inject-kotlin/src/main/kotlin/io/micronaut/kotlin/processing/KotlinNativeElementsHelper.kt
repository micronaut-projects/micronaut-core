/*
 * Copyright 2017-2024 original authors
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

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import io.micronaut.inject.utils.NativeElementsHelper
import io.micronaut.kotlin.processing.visitor.KotlinClassNativeElement
import io.micronaut.kotlin.processing.visitor.KotlinMethodNativeElement

/**
 * The Kotlin native element helper.
 *
 * @author Denis Stepanov
 * @since 4.3.0
 */
internal class KotlinNativeElementsHelper(var resolver: Resolver) : NativeElementsHelper<KSClassDeclaration, KSFunctionDeclaration>() {

    override fun getClassCacheKey(classElement: KSClassDeclaration): Any {
        return KotlinClassNativeElement(classElement, null, null)
    }

    override fun getMethodCacheKey(methodElement: KSFunctionDeclaration): Any {
        return KotlinMethodNativeElement(methodElement)
    }

    fun findOverriddenMethods(methodElement: KSFunctionDeclaration): MutableCollection<KSFunctionDeclaration> {
        var parent = methodElement.parent
        if (parent is KSPropertyDeclaration) {
            parent = parent.parent
        }
        if (parent is KSFunctionDeclaration) {
            parent = parent.parent
        }
        return super.findOverriddenMethods(parent as KSClassDeclaration, methodElement)
    }

    override fun overrides(m1: KSFunctionDeclaration, m2: KSFunctionDeclaration, owner: KSClassDeclaration): Boolean {
        return resolver.overrides(m1, m2)
    }

    override fun getMethodName(element: KSFunctionDeclaration): String {
       return element.simpleName.asString()
    }

    override fun getSuperClass(classNode: KSClassDeclaration): KSClassDeclaration? {
        val superTypes = classNode.superTypes
        for (superclass in superTypes) {
            val resolved = superclass.resolve()
            val declaration = resolved.declaration
            if (declaration is KSClassDeclaration) {
                if (declaration.classKind == ClassKind.CLASS && declaration.qualifiedName?.asString() != Any::class.qualifiedName) {
                    return declaration
                }
            }
        }
        return null
    }

    override fun getInterfaces(classNode: KSClassDeclaration): MutableCollection<KSClassDeclaration> {
        val superTypes = classNode.superTypes
        val result: MutableCollection<KSClassDeclaration> = ArrayList()
        for (superclass in superTypes) {
            val resolved = superclass.resolve()
            val declaration = resolved.declaration
            if (declaration is KSClassDeclaration) {
                if (declaration.classKind == ClassKind.INTERFACE) {
                    result.add(declaration)
                }
            }
        }
        return result
    }

    override fun getMethods(classNode: KSClassDeclaration): MutableList<KSFunctionDeclaration> {
        return classNode.getDeclaredFunctions().toMutableList()
    }

    override fun excludeClass(classNode: KSClassDeclaration): Boolean {
        val t = classNode.asStarProjectedType()
        val builtIns = resolver.builtIns
        return t == builtIns.anyType ||
                t == builtIns.nothingType ||
                t == builtIns.unitType ||
                (classNode.qualifiedName != null && (
                        classNode.qualifiedName!!.asString() == Enum::class.java.name ||
                                classNode.qualifiedName!!.asString() == Record::class.java.name
                        ))
    }

    override fun isInterface(classNode: KSClassDeclaration): Boolean {
        return classNode.classKind == ClassKind.INTERFACE
    }

}
