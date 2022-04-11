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

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.order.Ordered
import io.micronaut.inject.visitor.TypeElementVisitor
import org.omg.CORBA.Object
import java.util.*

class LoadedVisitor(val visitor: TypeElementVisitor<*, *>,
                    val visitorContext: KotlinVisitorContext): Ordered {

    companion object {
        const val ANY = "kotlin.Any"
    }

    var classAnnotation: String = ANY
    var elementAnnotation: String = ANY

    init {
        val javaClass = visitor.javaClass
        val resolver = visitorContext.resolver
        val declaration = resolver.getClassDeclarationByName(javaClass.name)
        val tevClassName = TypeElementVisitor::class.java.name

        if (declaration != null) {
            val reference = declaration.superTypes
                .map { it.resolve() }
                .find {
                    it.declaration.qualifiedName?.asString() == tevClassName
                }!!
            classAnnotation = getType(reference.arguments[0].type!!.resolve(), visitor.classType)
            elementAnnotation = getType(reference.arguments[1].type!!.resolve(), visitor.elementType)
        }
        if (classAnnotation == ANY) {
            classAnnotation = Object::class.java.name
        }
        if (elementAnnotation == ANY) {
            elementAnnotation = Object::class.java.name
        }
    }

    private fun getType(type: KSType, default: String): String {
        return if (!type.isError) {
            val elementAnnotation = type.declaration.qualifiedName!!.asString()
            if (elementAnnotation == ANY) {
                default
            } else {
                elementAnnotation
            }
        } else {
            //sigh
            UUID.randomUUID().toString()
        }
    }

    fun matches(classDeclaration: KSClassDeclaration): Boolean {
        if (classAnnotation == "java.lang.Object") {
            return true
        }
        val annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(classDeclaration)

        return annotationMetadata.hasStereotype(classAnnotation)
    }

    fun matches(annotationMetadata: AnnotationMetadata): Boolean {
        if (elementAnnotation == "java.lang.Object") {
            return true
        }
        return annotationMetadata.hasStereotype(elementAnnotation)
    }
}
