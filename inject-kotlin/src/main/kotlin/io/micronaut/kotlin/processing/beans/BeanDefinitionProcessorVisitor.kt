package io.micronaut.kotlin.processing.beans

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.google.devtools.ksp.visitor.KSTopDownVisitor
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.configuration.ConfigurationMetadata
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext

class BeanDefinitionProcessorVisitor(private val classDeclaration: KSClassDeclaration,
                                     private val visitorContext: KotlinVisitorContext) {

    val beanDefinitionWriters: MutableMap<KSAnnotated, BeanDefinitionWriter> = mutableMapOf()
    private var beanWriter: BeanDefinitionVisitor? = null
    private val isAopProxyType: Boolean
    private val isExecutableType: Boolean
    private val isDeclaredBean: Boolean
    private val isConfigurationProperties: Boolean
    private val isFactoryClass: Boolean
    private val concreteClassElement: ClassElement = visitorContext.elementFactory.newClassElement(classDeclaration.asStarProjectedType())
    private val configurationMetadataBuilder = KotlinConfigurationMetadataBuilder()
    private var configurationMetadata: ConfigurationMetadata? = null

    init {
        this.isAopProxyType = hasAroundStereotype(concreteClassElement.annotationMetadata) &&
                !concreteClassElement.isAbstract &&
                !concreteClassElement.isAssignable(Interceptor::class.java)
        this.isExecutableType = isAopProxyType || concreteClassElement.hasStereotype(Executable::class.java)
        this.isConfigurationProperties = concreteClassElement.hasDeclaredStereotype(ConfigurationReader::class.java)
        this.isFactoryClass = concreteClassElement.hasStereotype(Factory::class.java)
        this.isDeclaredBean = isExecutableType ||
                isConfigurationProperties ||
                isFactoryClass ||
                concreteClassElement.hasStereotype(AnnotationUtil.SCOPE) ||
                concreteClassElement.hasStereotype(DefaultScope::class.java) ||
                concreteClassElement.hasDeclaredStereotype(Bean::class.java) ||
                concreteClassElement.primaryConstructor.filter { it.hasStereotype(AnnotationUtil.INJECT) }.isPresent

        if (isDeclaredBean) {
            defineBeanDefinition()
        }
    }

    companion object {
        fun hasAroundStereotype(annotationMetadata: AnnotationMetadata): Boolean {
            if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_AROUND)) {
                return true
            } else {
                if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
                    return annotationMetadata.getAnnotationValuesByType(InterceptorBinding::class.java)
                        .stream().anyMatch{ av ->
                            av.enumValue("kind", InterceptorKind::class.java).orElse(InterceptorKind.AROUND) == InterceptorKind.AROUND
                        }
                }
            }
            return false
        }
    }

    fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Any): Any {
        val owningClass = this.classDeclaration == classDeclaration
        val classElement = if (owningClass) {
            concreteClassElement
        } else {
            visitorContext.elementFactory.newClassElement(classDeclaration.asStarProjectedType())
        }
        if (owningClass && concreteClassElement.isAbstract && !isDeclaredBean) {
            return data
        }

        var superType = classElement.superType.orElse(null)
        val superClasses = mutableListOf<ClassElement>()
        while (superType != null) {
            superClasses.add(superType)
            superType = superType.superType.orElse(null)
        }
        superClasses.reverse()
/*        for (clazz in superClasses) {
            (clazz.nativeType as KSClassDeclaration).accept(this, data)
        }*/
        return data
    }



    private fun defineBeanDefinition() {
        if (!beanDefinitionWriters.contains(classDeclaration)) {
            if (classDeclaration.packageName.asString().isEmpty()) {
                visitorContext.fail("Micronaut beans cannot be in the default package", classDeclaration)
                return
            }

            val beanWriter = BeanDefinitionWriter(concreteClassElement, configurationMetadataBuilder, visitorContext)
            beanWriter.visitTypeArguments(concreteClassElement.allTypeArguments)
            beanDefinitionWriters[classDeclaration] = beanWriter

            val constructor = concreteClassElement.primaryConstructor.orElse(null)

            if (constructor != null) {
                if (constructor.parameters.isEmpty()) {
                    beanWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA, visitorContext)
                } else {
                    val constructorMetadata = constructor.annotationMetadata
                    val isConstructBinding = constructorMetadata.hasDeclaredStereotype(ConfigurationInject::class.java)
                    if (isConstructBinding) {
                        this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                            classDeclaration,
                            null)
                    }
                    beanWriter.visitBeanDefinitionConstructor(constructor, constructor.isPrivate, visitorContext)
                }

            } else {
                val defaultConstructor = concreteClassElement.defaultConstructor.orElse(null)
                if (defaultConstructor == null) {
                    visitorContext.fail("Class must have at least one non private constructor in order to be a candidate for dependency injection", classDeclaration)
                } else {
                    beanWriter.visitDefaultConstructor(defaultConstructor.annotationMetadata, visitorContext)
                }
            }
        } else {
            beanWriter = beanDefinitionWriters[classDeclaration]
        }
    }
}
