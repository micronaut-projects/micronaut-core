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
import java.util.*
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
    private val readPrefixes: Array<String>
    private val writePrefixes: Array<String>

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
        this.readPrefixes = classElement.getValue(
            AccessorsStyle::class.java,
            "readPrefixes",
            Array<String>::class.java)
            .orElse(arrayOf(AccessorsStyle.DEFAULT_READ_PREFIX))
        this.writePrefixes = classElement.getValue(
            AccessorsStyle::class.java,
            "writePrefixes",
            Array<String>::class.java)
            .orElse(arrayOf(AccessorsStyle.DEFAULT_WRITE_PREFIX))
    }

    companion object {
        private const val ANN_CONSTRAINT = "javax.validation.Constraint"
        private const val ANN_VALID = "javax.validation.Valid"
        private val IS_CONSTRAINT: (AnnotationMetadata) -> Boolean = { am: AnnotationMetadata ->
            am.hasStereotype(ANN_CONSTRAINT) || am.hasStereotype(ANN_VALID)
        }
        private const val ANN_VALIDATED = "io.micronaut.validation.Validated"

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
        }
        classElement.getEnclosedElements(ElementQuery.of(ClassElement::class.java).onlyAccessible())
            .forEach(this::visitInnerClass)
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
        } else if (isConfigurationProperties) {
            if (propertyElement.hasStereotype(ConfigurationBuilder::class.java)) {
                visitConfigurationBuilder(propertyElement)
            } else if (propertyElement.writeMethod.isPresent) {
                val methodElement = propertyElement.writeMethod.get()
                visitConfigPropsSetter(methodElement, propertyElement, propertyElement.name)
            }
            if (!beanWriter!!.isValidated) {
                beanWriter!!.isValidated = IS_CONSTRAINT.invoke(propertyElement)
            }
        }
    }

    private fun visitMethod(methodElement: MethodElement) {
        var hasConstraints = false
        if (isDeclaredBean && !methodElement.hasStereotype(ANN_VALIDATED)) {
            if (methodElement.parameters.any(IS_CONSTRAINT)) {
                hasConstraints = true
                methodElement.annotate(ANN_VALIDATED)
            }
        }
        if (isFactoryClass && methodElement !is ConstructorElement && methodElement.hasDeclaredStereotype(Bean::class.qualifiedName, AnnotationUtil.SCOPE)) {
            visitFactoryMethod(methodElement)
        } else if (isDeclaredBean && methodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
            beanWriter!!.visitPostConstructMethod(
                methodElement.declaringType,
                methodElement,
                false,
                visitorContext
            )
        } else if (isDeclaredBean && methodElement.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)) {
            beanWriter!!.visitPreDestroyMethod(
                methodElement.declaringType,
                methodElement,
                false,
                visitorContext
            )
        } else if (methodElement.hasStereotype(AnnotationUtil.INJECT)) {
            beanWriter!!.visitMethodInjectionPoint(
                methodElement.declaringType,
                methodElement,
                false,
                visitorContext
            )
        } else if (isConfigurationProperties) {
            if (NameUtils.isWriterName(methodElement.name, writePrefixes) && methodElement.parameters.size == 1) {
                visitConfigPropsSetter(
                    methodElement,
                    methodElement,
                    NameUtils.getPropertyNameForSetter(methodElement.name, writePrefixes)
                )
            } else if (NameUtils.isReaderName(methodElement.name, readPrefixes) && methodElement.parameters.isEmpty()) {
                if (!beanWriter!!.isValidated) {
                    beanWriter!!.isValidated = IS_CONSTRAINT.invoke(methodElement)
                }
            }
        } else {
            val isExecutable = isExecutable(methodElement) || hasAroundStereotype(methodElement)

            if (isExecutable || (isDeclaredBean && hasConstraints)) {
                val preprocess = methodElement.booleanValue(Executable::class.java, "processOnStartup").orElse(false)
                if (preprocess) {
                    beanWriter!!.setRequiresMethodProcessing(true)
                }

                beanWriter!!.visitExecutableMethod(
                    methodElement.declaringType,
                    methodElement,
                    visitorContext
                )
            }
        }
    }

    private fun isExecutable(methodElement: MethodElement): Boolean {
        if (methodElement.hasDeclaredStereotype(Executable::class.java)) {
            return true
        }
        if (isExecutableType) {
            if (classElement == methodElement.declaringType || methodElement.isPublic) {
                return true
            }
        }
        return false
    }

    private fun visitConfigPropsSetter(methodElement: MethodElement, annotatedElement: Element, name: String) {
        if (shouldExclude(configurationMetadata!!, name)) {
            return
        }
        val parameterElement = methodElement.parameters[0]
        val propertyMetadata = configurationMetadataBuilder.visitProperty(
            classElement,
            methodElement.declaringType,
            parameterElement.type.name,
            name,
            null,
            null
        )

        annotatedElement.annotate(Property::class.qualifiedName!!) { builder: AnnotationValueBuilder<Property> ->
            builder.member("name", propertyMetadata.path)
        }

        beanWriter!!.visitSetterValue(
            methodElement.declaringType,
            methodElement,
            false,
            true
        )
    }

    private fun visitConfigurationBuilder(propertyElement: PropertyElement) {
        propertyElement.readMethod.ifPresent { readMethod ->
            beanWriter!!.visitConfigBuilderMethod(
                propertyElement.type,
                readMethod.name,
                readMethod.annotationMetadata,
                configurationMetadataBuilder,
                propertyElement.type.isInterface)
            try {
                KotlinConfigurationBuilderVisitor(propertyElement, configurationMetadataBuilder, beanWriter!!).visit()
            } finally {
                beanWriter!!.visitConfigBuilderEnd()
            }
        }
    }

    private fun shouldExclude(includes: Set<String>, excludes: Set<String>, propertyName: String): Boolean {
        if (includes.isNotEmpty() && !includes.contains(propertyName)) {
            return true
        }
        if (excludes.isNotEmpty() && excludes.contains(propertyName)) {
            return true
        }
        return false
    }

    private fun shouldExclude(configurationMetadata: ConfigurationMetadata, propertyName: String): Boolean {
        return shouldExclude(configurationMetadata.includes, configurationMetadata.excludes, propertyName)
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
            classElement.annotate(ConfigurationReader::class.java) { builder: AnnotationValueBuilder<ConfigurationReader> ->
                builder.member("prefix", computedPrefix)
            }
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
