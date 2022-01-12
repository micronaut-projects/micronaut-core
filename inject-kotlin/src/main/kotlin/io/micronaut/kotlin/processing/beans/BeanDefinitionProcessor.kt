package io.micronaut.kotlin.processing.beans

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Generated
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import javax.lang.model.element.ElementVisitor

class BeanDefinitionProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

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

        val beanDefinitionWriters = mutableListOf<BeanDefinitionWriter>()
        for (classDeclaration in elements) {
            val classElement = visitorContext.elementFactory.newClassElement(classDeclaration.asStarProjectedType())

            if (classElement.isInner && !classElement.isStatic) {
                continue
            }
            if (classElement.isInterface) {
                if (classElement.hasStereotype(AnnotationUtil.ANN_INTRODUCTION) ||
                    classElement.hasStereotype(ConfigurationReader::class.java)) {
                    visit(classDeclaration, beanDefinitionWriters, visitorContext)
                }
            } else {
                visit(classDeclaration, beanDefinitionWriters, visitorContext)
            }
        }
        return emptyList()
    }

    private fun visit(classDeclaration: KSClassDeclaration, beanDefinitionWriters: MutableList<BeanDefinitionWriter>, visitorContext: KotlinVisitorContext) {
        val visitor = BeanDefinitionVisitor(classDeclaration, visitorContext)
        classDeclaration.accept(visitor, Object())
        beanDefinitionWriters.addAll(visitor.beanDefinitionWriters)
    }
}
