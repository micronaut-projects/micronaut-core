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

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFile
import io.micronaut.inject.ast.Element
import io.micronaut.inject.writer.AbstractClassWriterOutputVisitor
import io.micronaut.inject.writer.GeneratedFile
import io.micronaut.kotlin.processing.visitor.AbstractKotlinElement
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.io.File
import java.io.OutputStream
import java.util.*

internal class KotlinOutputVisitor(private val environment: SymbolProcessorEnvironment): AbstractClassWriterOutputVisitor(false) {

    override fun visitClass(classname: String, vararg originatingElements: Element): OutputStream {
        return environment.codeGenerator.createNewFile(
            getNativeElements(originatingElements),
            classname.substringBeforeLast('.'),
            classname.substringAfterLast('.'),
            "class")
    }

    override fun visitServiceDescriptor(type: String, classname: String, originatingElement: Element) {
        environment.codeGenerator.createNewFile(
            getNativeElements(arrayOf(originatingElement)),
            "META-INF.micronaut",
            "${type}${File.separator}${classname}",
            "").use {
            it.bufferedWriter().write("")
        }
    }

    override fun visitMetaInfFile(path: String, vararg originatingElements: Element): Optional<GeneratedFile> {
        val elements = path.split(File.separator).toMutableList()
        elements.add(0, "META-INF")
        return Optional.of(KotlinVisitorContext.KspGeneratedFile(environment, elements, getNativeElements(originatingElements)))
    }

    override fun visitGeneratedFile(path: String): Optional<GeneratedFile> {
        val elements = path.split(File.separator).toMutableList()
        return Optional.of(KotlinVisitorContext.KspGeneratedFile(environment, elements, Dependencies(aggregating = true, sources = emptyArray())))
    }

    override fun visitGeneratedFile(path: String, vararg originatingElements: Element): Optional<GeneratedFile> {
        val elements = path.split(File.separator).toMutableList()
        return Optional.of(KotlinVisitorContext.KspGeneratedFile(environment, elements, getNativeElements(originatingElements)))
    }

    private fun getNativeElements(originatingElements: Array<out Element>): Dependencies {
        val originatingFiles: MutableList<KSFile> = ArrayList(originatingElements.size)
        for (originatingElement in originatingElements) {
            if (originatingElement is AbstractKotlinElement<*>) {
                val nativeType = originatingElement.nativeType.element.containingFile
                if (nativeType is KSFile) {
                    originatingFiles.add(nativeType)
                }
            }
        }
        return Dependencies(aggregating = originatingElements.size > 1, sources = originatingFiles.toTypedArray())
    }
}
