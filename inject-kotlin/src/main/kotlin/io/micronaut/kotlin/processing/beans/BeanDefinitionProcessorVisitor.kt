package io.micronaut.kotlin.processing.beans

import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.ast.*
import io.micronaut.inject.configuration.ConfigurationMetadata
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.inject.writer.OriginatingElements
import io.micronaut.kotlin.processing.visitor.KotlinClassElement
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.util.concurrent.atomic.AtomicInteger

class BeanDefinitionProcessorVisitor(private val classElement: KotlinClassElement,
                                     private val visitorContext: KotlinVisitorContext) {

    val beanDefinitionWriters: MutableList<BeanDefinitionWriter> = mutableListOf()
    private var beanWriter: BeanDefinitionVisitor? = null
    private val isAopProxyType: Boolean
    private val isExecutableType: Boolean
    private val isDeclaredBean: Boolean
    private val isConfigurationProperties: Boolean
    private val isFactoryClass: Boolean
    private val configurationMetadataBuilder = KotlinConfigurationMetadataBuilder()
    private var configurationMetadata: ConfigurationMetadata? = null
    private val factoryMethodIndex = AtomicInteger(0)

    init {
        this.isAopProxyType = hasAroundStereotype(classElement.annotationMetadata) &&
                !classElement.isAbstract &&
                !classElement.isAssignable(Interceptor::class.java)
        this.isExecutableType = isAopProxyType || classElement.hasStereotype(Executable::class.java)
        this.isConfigurationProperties = classElement.hasDeclaredStereotype(ConfigurationReader::class.java)
        if (isConfigurationProperties) {
            this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                classElement,
                null
            )
        }
        this.isFactoryClass = classElement.hasStereotype(Factory::class.java)
        val hasQualifier = classElement.hasStereotype(AnnotationUtil.QUALIFIER) && !classElement.isAbstract
        this.isDeclaredBean = isExecutableType ||
                isConfigurationProperties ||
                isFactoryClass ||
                hasQualifier ||
                classElement.hasStereotype(AnnotationUtil.SCOPE) ||
                classElement.hasStereotype(DefaultScope::class.java) ||
                classElement.hasDeclaredStereotype(Bean::class.java) ||
                classElement.primaryConstructor.filter { it.hasStereotype(AnnotationUtil.INJECT) }.isPresent
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

    fun visit() {
        if (isDeclaredBean) {
            if (isAopProxyType && classElement.isFinal) {
                visitorContext.fail("Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement.name, classElement)
                return
            }
            if (classElement.packageName.isEmpty()) {
                visitorContext.fail("Micronaut beans cannot be in the default package", classElement)
                return
            }
            defineBeanDefinition()

            classElement.getEnclosedElements(ElementQuery.of(PropertyElement::class.java).onlyAccessible())
                .forEach(this::visitProperty)
            classElement.getEnclosedElements(ElementQuery.of(MethodElement::class.java).onlyAccessible())
                .forEach(this::visitMethod)
            classElement.getEnclosedElements(ElementQuery.of(ClassElement::class.java).onlyAccessible())
                .forEach(this::visitInnerClass)
        }
    }

    private fun visitInnerClass(classElement: ClassElement) {
        val visitor = BeanDefinitionProcessorVisitor(classElement as KotlinClassElement, visitorContext)
        visitor.visit()
        beanDefinitionWriters.addAll(visitor.beanDefinitionWriters)
    }

    private fun visitProperty(propertyElement: PropertyElement) {
        if (isFactoryClass && propertyElement.isFinal && propertyElement.hasDeclaredStereotype(Bean::class.java)) {
            if (propertyElement.isPrivate) {
                visitorContext.fail("Beans produced from properties cannot be private", propertyElement)
            } else {
                visitFactoryProperty(propertyElement)
            }
        }
        if (propertyElement.hasStereotype(AnnotationUtil.INJECT) || propertyElement.hasStereotype(AnnotationUtil.QUALIFIER)) {
            propertyElement.writeMethod.ifPresent { wm ->
                beanWriter!!.visitMethodInjectionPoint(
                    propertyElement.declaringType,
                    wm,
                    false,
                    visitorContext
                )
            }
        } else if (isConfigurationProperties && propertyElement.writeMethod.isPresent) {
            val methodElement = propertyElement.writeMethod.get()
            val parameterElement = methodElement.parameters[0]
            val propertyMetadata = configurationMetadataBuilder.visitProperty(
                classElement,
                propertyElement.declaringType,
                parameterElement.type.name,
                propertyElement.name,
                null,
                null
            )

            propertyElement.annotate(Property::class.qualifiedName!!) { builder: AnnotationValueBuilder<Property> ->
                builder.member("name", propertyMetadata.path)
            }

            beanWriter!!.visitSetterValue(
                methodElement.declaringType,
                methodElement,
                false,
                true
            )
        }
    }

    private fun visitMethod(methodElement: MethodElement) {
        if (isFactoryClass && methodElement !is ConstructorElement && methodElement.hasDeclaredStereotype(Bean::class.qualifiedName, AnnotationUtil.SCOPE)) {
            visitFactoryMethod(methodElement)
        }
    }

    private fun visitFactoryMethod(methodElement: MethodElement) {
        val producedClassElement = methodElement.genericReturnType
        val beanMethodWriter = BeanDefinitionWriter(
            methodElement,
            OriginatingElements.of(classElement),
            configurationMetadataBuilder,
            visitorContext,
            factoryMethodIndex.getAndIncrement())

        val allTypeArguments = producedClassElement.allTypeArguments
        beanMethodWriter.visitTypeArguments(allTypeArguments)
        beanMethodWriter.visitBeanFactoryMethod(
            classElement,
            methodElement
        )
        handlePreDestroy(methodElement, producedClassElement, beanMethodWriter)
        beanDefinitionWriters.add(beanMethodWriter)
    }

    private fun visitFactoryProperty(propertyElement: PropertyElement) {
        val beanDefinitionWriter = BeanDefinitionWriter(
            propertyElement,
            OriginatingElements.of(classElement),
            configurationMetadataBuilder,
            visitorContext,
            factoryMethodIndex.getAndIncrement()
        )
        beanDefinitionWriter.visitTypeArguments(propertyElement.genericType.allTypeArguments)
        beanDefinitionWriter.visitBeanFactoryMethod(
            classElement,
            propertyElement.readMethod.get()
        )
        handlePreDestroy(propertyElement, propertyElement.genericType, beanDefinitionWriter)
        beanDefinitionWriters.add(beanDefinitionWriter)
    }

    private fun handlePreDestroy(element: Element, producedType: ClassElement, beanDefinitionWriter: BeanDefinitionWriter) {
        val preDestroy = element.stringValue(Bean::class.java, "preDestroy")
            .filter { method -> method.isNotEmpty() }

        if (preDestroy.isPresent) {
            val destroyMethodName = preDestroy.get()
            val destroyMethod = producedType
                .getEnclosedElement(ElementQuery.ALL_METHODS
                    .onlyAccessible()
                    .onlyInstance()
                    .filter { me -> me.parameters.isEmpty() }
                    .filter { me -> me.name.equals(destroyMethodName) })

            if (destroyMethod.isPresent) {
                beanDefinitionWriter.visitPreDestroyMethod(
                    classElement,
                    destroyMethod.get(),
                    false,
                    visitorContext
                )
            } else {
                visitorContext.fail(
                    "The specified preDestroy method does not exist or is not public: $destroyMethodName",
                    element
                )
            }
        }
    }

    private fun defineBeanDefinition() {
        if (configurationMetadata != null) {
            val existingPrefix = classElement.stringValue(ConfigurationReader::class.java, "prefix")
                .orElse("")
            val computedPrefix = if (StringUtils.isNotEmpty(existingPrefix)) {
                existingPrefix + "." + configurationMetadata!!.name
            } else {
                configurationMetadata!!.name
            }

            classElement.mutateMember(
                ConfigurationReader::class.qualifiedName!!,
                "prefix",
                computedPrefix
            )
        }

        val beanWriter = BeanDefinitionWriter(classElement, configurationMetadataBuilder, visitorContext)
        beanWriter.visitTypeArguments(classElement.allTypeArguments)
        beanDefinitionWriters.add(beanWriter)

        val constructor = classElement.primaryConstructor.orElse(null)

        if (constructor != null) {
            if (constructor.parameters.isEmpty()) {
                beanWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA, visitorContext)
            } else {
                val constructorMetadata = constructor.annotationMetadata
                val isConstructBinding = constructorMetadata.hasDeclaredStereotype(ConfigurationInject::class.java)
                if (isConstructBinding) {
                    this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                        classElement,
                        null)
                }
                beanWriter.visitBeanDefinitionConstructor(constructor, constructor.isPrivate, visitorContext)
            }

        } else {
            val defaultConstructor = classElement.defaultConstructor.orElse(null)
            if (defaultConstructor == null) {
                visitorContext.fail("Class must have at least one non private constructor in order to be a candidate for dependency injection", classElement)
            } else {
                beanWriter.visitDefaultConstructor(defaultConstructor.annotationMetadata, visitorContext)
            }
        }

        this.beanWriter = beanWriter
    }
}
