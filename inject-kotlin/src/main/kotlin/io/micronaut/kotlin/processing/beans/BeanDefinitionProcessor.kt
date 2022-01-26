package io.micronaut.kotlin.processing.beans

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Generated
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.kotlin.processing.KotlinOutputVisitor
import io.micronaut.kotlin.processing.visitor.KotlinClassElement
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext

class BeanDefinitionProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

    private val beanDefinitionWriters = mutableListOf<BeanDefinitionWriter>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val visitorContext = KotlinVisitorContext(environment, resolver)

        val elements = resolver.getAllFiles()
            .flatMap { file: KSFile -> file.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration: KSClassDeclaration ->
                declaration.annotations.none { ksAnnotation ->
                    ksAnnotation.shortName.getQualifier() == Generated::class.simpleName
                }
            }
            .toList()

        for (classDeclaration in elements) {
            val classElement = visitorContext.elementFactory.newClassElement(classDeclaration.asStarProjectedType()) as KotlinClassElement

            if (classElement.isInner && !classElement.isStatic) {
                continue
            }
            if (classDeclaration.classKind == ClassKind.ANNOTATION_CLASS) {
                continue
            }
            if (classElement.isInterface) {
                if (classElement.hasStereotype(AnnotationUtil.ANN_INTRODUCTION) ||
                    classElement.hasStereotype(ConfigurationReader::class.java)) {
                    visit(classElement, beanDefinitionWriters, visitorContext)
                }
            } else {
                visit(classElement, beanDefinitionWriters, visitorContext)
            }
        }
        return emptyList()
    }

    override fun finish() {
        val outputVisitor = KotlinOutputVisitor(environment)
        for (beanDefWriter in beanDefinitionWriters) {
            val beanReferenceWriter = BeanDefinitionReferenceWriter(beanDefWriter)
            beanReferenceWriter.setRequiresMethodProcessing(beanDefWriter.requiresMethodProcessing())
            beanReferenceWriter.setContextScope(beanDefWriter.annotationMetadata.hasDeclaredAnnotation(Context::class.java))
            beanDefWriter.visitBeanDefinitionEnd()
            beanReferenceWriter.accept(outputVisitor)
            beanDefWriter.accept(outputVisitor)
        }
        if (beanDefinitionWriters.isNotEmpty()) {
            outputVisitor.finish()
        }
    }

    private fun visit(classElement: KotlinClassElement, beanDefinitionWriters: MutableList<BeanDefinitionWriter>, visitorContext: KotlinVisitorContext) {
        val visitor = BeanDefinitionProcessorVisitor(classElement, visitorContext)
        visitor.visit()
        beanDefinitionWriters.addAll(visitor.beanDefinitionWriters)
    }


}
