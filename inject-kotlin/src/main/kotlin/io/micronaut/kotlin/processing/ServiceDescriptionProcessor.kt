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

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import io.micronaut.core.annotation.Generated

class ServiceDescriptionProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

    private val classWriterOutputVisitor = KotlinOutputVisitor(environment)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val elements = resolver.getAllFiles()
            .flatMap { file: KSFile -> file.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration: KSClassDeclaration ->
                declaration.annotations.any { ksAnnotation ->
                    ksAnnotation.shortName.getQualifier() == Generated::class.simpleName
                }
            }
            .toList()

        return emptyList()
    }

    override fun finish() {
        super.finish()
    }
}
