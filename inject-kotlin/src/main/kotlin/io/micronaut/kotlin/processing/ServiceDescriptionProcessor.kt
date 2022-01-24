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
