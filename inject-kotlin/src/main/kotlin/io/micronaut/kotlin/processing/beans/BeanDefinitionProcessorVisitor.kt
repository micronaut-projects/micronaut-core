package io.micronaut.kotlin.processing.beans

import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil
import io.micronaut.aop.writer.AopProxyWriter
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.util.ArrayUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.annotation.AnnotationMetadataReference
import io.micronaut.inject.ast.*
import io.micronaut.inject.configuration.ConfigurationMetadata
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.inject.writer.OriginatingElements
import io.micronaut.kotlin.processing.visitor.KotlinClassElement
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class BeanDefinitionProcessorVisitor(private val classElement: KotlinClassElement,
                                     private val visitorContext: KotlinVisitorContext) {

    val beanDefinitionWriters: MutableList<BeanDefinitionVisitor> = mutableListOf()
    private var beanWriter: BeanDefinitionVisitor? = null
    private var aopProxyWriter: AopProxyWriter? = null
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
        const val ANN_VALIDATED = "io.micronaut.validation.Validated"
        const val ANN_CONFIGURATION_ADVICE = "io.micronaut.runtime.context.env.ConfigurationAdvice"

        fun hasAroundStereotype(annotationMetadata: AnnotationMetadata, declared: Boolean = false): Boolean {
            return if (declared) {
                hasAroundStereotype(annotationMetadata::hasDeclaredStereotype, annotationMetadata::getDeclaredAnnotationValuesByType)
            } else {
                hasAroundStereotype(annotationMetadata::hasStereotype, annotationMetadata::getAnnotationValuesByType)
            }
        }

        private fun <T: Annotation> hasAroundStereotype(hasAnnotation: (String) -> Boolean, annotationValues: (Class<T>) -> List<AnnotationValue<T>>): Boolean {
            if (hasAnnotation.invoke(AnnotationUtil.ANN_AROUND)) {
                return true
            } else {
                if (hasAnnotation.invoke(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
                    return annotationValues.invoke(InterceptorBinding::class.java as Class<T>)
                        .any { av ->
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
            if (classElement.isEnum) {
                visitorContext.fail("Enum types cannot be defined as beans", classElement)
                return
            }
            if (classElement.packageName.isEmpty()) {
                visitorContext.fail("Micronaut beans cannot be in the default package", classElement)
                return
            }
            defineBeanDefinition()

            classElement.getEnclosedElements(ElementQuery.of(PropertyElement::class.java))
                .forEach(this::visitProperty)
            classElement.getEnclosedElements(ElementQuery.of(MethodElement::class.java))
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
                return
            } else if (propertyElement.isProtected) {
                visitorContext.fail("Beans produced from properties cannot be protected", propertyElement)
                return
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
            val hasAround = hasAroundStereotype(methodElement)
            val isExecutable = isExecutable(methodElement) || hasAround

            if (isExecutable || (isDeclaredBean && hasConstraints)) {

                if (methodElement.isPrivate) {
                    if (methodElement.hasDeclaredStereotype(Executable::class.java) ||
                        hasAroundStereotype(methodElement, true)) {
                        visitorContext.fail("Method annotated as executable but is declared private. Change the method to be non-private in order for AOP advice to be applied.", methodElement)
                        return
                    }
                }

                val preprocess = methodElement.booleanValue(Executable::class.java, "processOnStartup").orElse(false)
                if (preprocess) {
                    beanWriter!!.setRequiresMethodProcessing(true)
                }

                val isPublicMethodInProxyType = isAopProxyType && (methodElement.isPublic || methodElement.isPackagePrivate)
                val hasAroundNotAbstractNotInterceptor = hasAround && !classElement.isAbstract && !classElement.isAssignable(Interceptor::class.java)

                if (isPublicMethodInProxyType || hasAroundNotAbstractNotInterceptor) {
                    visitAroundMethod(methodElement, isPublicMethodInProxyType && !hasAroundNotAbstractNotInterceptor)
                } else {
                    beanWriter!!.visitExecutableMethod(
                        methodElement.declaringType,
                        methodElement,
                        visitorContext
                    )
                }
            }
        }
    }

    private fun isExecutable(methodElement: MethodElement): Boolean {
        if (methodElement.hasStereotype(Executable::class.java)) {
            return true
        }
        if (isExecutableType) {
            if (classElement == methodElement.declaringType || methodElement.isPublic) {
                return true
            }
        }
        return false
    }

    private fun visitAroundMethod(methodElement: MethodElement, inheritClassMetadata: Boolean) {
        if (methodElement.isFinal) {
            if (hasAroundStereotype(methodElement, true)) {
                visitorContext.fail(
                    "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.",
                    methodElement
                )
            } else {
                visitorContext.fail(
                    "Public method inherits AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.",
                    methodElement
                )
            }
            return
        }

        if (aopProxyWriter == null) {
            createClassProxyWriter(beanWriter!!)
        }
        val aopMethod = if (inheritClassMetadata) {
            methodElement.withNewMetadata(AnnotationMetadataHierarchy(methodElement.declaringType.annotationMetadata, methodElement.annotationMetadata))
        } else {
            methodElement
        }

        if (hasAroundStereotype(aopMethod)) {
            val interceptorTypeReferences = InterceptedMethodUtil
                .resolveInterceptorBinding(aopMethod, InterceptorKind.AROUND)
            aopProxyWriter!!.visitInterceptorBinding(*interceptorTypeReferences)
        }

        aopProxyWriter!!.visitAroundMethod(
            classElement,
            aopMethod
        )
    }

    private fun visitConfigPropsSetter(methodElement: MethodElement, annotatedElement: Element, name: String) {
        if (annotatedElement.isPrivate) {
            return
        }
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

    private fun visitFactoryMethod(methodElement_: MethodElement) {
        val producedClassElement = methodElement_.genericReturnType
        val newMetadata = AnnotationMetadataHierarchy(
            producedClassElement.annotationMetadata,
            methodElement_.annotationMetadata
        )
        val methodElement = methodElement_.withNewMetadata(newMetadata)
        val beanMethodWriter = BeanDefinitionWriter(
            methodElement,
            OriginatingElements.of(methodElement),
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
        if (methodElement.hasStereotype(AnnotationUtil.ANN_AROUND) && !classElement.isAbstract) {
            if (producedClassElement.isFinal) {
                visitorContext.fail("Cannot apply AOP advice to final class. Class must be made non-final to support proxying", methodElement)
                return
            }
            val proxyWriter = createProxyWriter(methodElement, beanMethodWriter,true)
            visitConstructor(proxyWriter, producedClassElement)
            proxyWriter.visitTypeArguments(allTypeArguments)
        } else if (methodElement.hasStereotype(Executable::class.java)) {
            handleFactoryExecutable(methodElement.genericReturnType, methodElement, beanMethodWriter)
        }
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
        if (hasAroundStereotype(propertyElement)) {
            handleFactoryPropertyAroundAdvice(propertyElement)
        } else if (propertyElement.hasStereotype(Executable::class.java)) {
            handleFactoryExecutable(propertyElement.genericType, propertyElement, beanDefinitionWriter)
        }
        beanDefinitionWriters.add(beanDefinitionWriter)
    }

    private fun handleFactoryPropertyAroundAdvice(propertyElement: PropertyElement) {
        if (propertyElement.isPrimitive) {
            visitorContext.fail("Cannot apply AOP advice to primitive beans", propertyElement)
            return
        } else if (propertyElement.isArray) {
            visitorContext.fail("Cannot apply AOP advice to arrays", propertyElement)
            return
        }
    }

    private fun handleFactoryExecutable(classElement: ClassElement,
                                        source: Element,
                                        beanMethodWriter: BeanDefinitionWriter) {

        if (classElement.isPrimitive) {
            visitorContext.fail("Using '@Executable' is not allowed on primitive type beans", source)
            return
        }
        if (classElement.isArray) {
            visitorContext.fail("Using '@Executable' is not allowed on array type beans", source)
            return
        }

        val newMetadata: AnnotationMetadata = AnnotationMetadataReference(
            beanMethodWriter.beanDefinitionName + BeanDefinitionReferenceWriter.REF_SUFFIX,
            source.annotationMetadata
        )

        classElement.getEnclosedElements(ElementQuery.ALL_METHODS
            .onlyAccessible()
            .modifiers { !it.contains(ElementModifier.FINAL) })
            .forEach { method ->
                beanMethodWriter.visitExecutableMethod(
                    method.declaringType,
                    method.withNewMetadata(newMetadata),
                    visitorContext
                )
            }
    }

    private fun handlePreDestroy(element: Element, producedType: ClassElement, beanDefinitionWriter: BeanDefinitionWriter) {
        val preDestroy = element.stringValue(Bean::class.java, "preDestroy")
            .filter { method -> method.isNotEmpty() }

        if (preDestroy.isPresent) {
            if (producedType.isPrimitive) {
                visitorContext.fail("Using 'preDestroy' is not allowed on primitive type beans", element)
                return
            } else if (producedType.isArray) {
                visitorContext.fail("Using 'preDestroy' is not allowed on array type beans", element)
                return
            }

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
            if (classElement.isInterface) {
                classElement.annotate(ANN_CONFIGURATION_ADVICE)
            }
        }

        val beanWriter = if (classElement.hasStereotype(AnnotationUtil.ANN_INTRODUCTION)) {
            createIntroductionAdviceWriter()
        } else {
            val beanDefinitionWriter = BeanDefinitionWriter(classElement, configurationMetadataBuilder, visitorContext)
            if (isAopProxyType) {
                createClassProxyWriter(beanDefinitionWriter)
            }
            beanDefinitionWriter
        }

        beanWriter.visitTypeArguments(classElement.allTypeArguments)
        beanDefinitionWriters.add(beanWriter)

        visitConstructor(beanWriter, classElement)

        this.beanWriter = beanWriter
    }

    private fun visitConstructor(beanDefinitionVisitor: BeanDefinitionVisitor,
                                 classElement: ClassElement) {
        val constructor = classElement.primaryConstructor.orElse(null)

        if (constructor != null) {
            /*
            if (constructor.parameters.isNotEmpty()) {
                val constructorMetadata = constructor.annotationMetadata
                val isConstructBinding = constructorMetadata.hasDeclaredStereotype(ConfigurationInject::class.java)
                if (isConstructBinding) {
                    this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                        classElement,
                        null)
                }
            }*/
            beanDefinitionVisitor.visitBeanDefinitionConstructor(constructor, constructor.isPrivate, visitorContext)
        } else {
            val defaultConstructor = classElement.defaultConstructor.orElse(null)
            if (defaultConstructor == null) {
                visitorContext.fail("Class must have at least one non private constructor in order to be a candidate for dependency injection", classElement)
            } else {
                beanDefinitionVisitor.visitDefaultConstructor(defaultConstructor.annotationMetadata, visitorContext)
            }
        }
    }

    private fun createIntroductionAdviceWriter(): AopProxyWriter {
        val annotationMetadata = classElement.annotationMetadata
        val packageName = classElement.packageName
        val beanClassName = classElement.simpleName
        val aroundInterceptors =
            InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND)
        val introductionInterceptors =
            InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.INTRODUCTION)
        val interfaceTypes = classElement.getIntroductionInterfaces()

        val interceptorTypes = ArrayUtils.concat(aroundInterceptors, *introductionInterceptors)
        val isInterface = classElement.isInterface
        val aopProxyWriter = AopProxyWriter(
            packageName,
            beanClassName,
            isInterface,
            classElement,
            annotationMetadata,
            interfaceTypes.toTypedArray(),
            visitorContext,
            configurationMetadataBuilder,
            configurationMetadata,
            *interceptorTypes
        )
        aopProxyWriter.visitTypeArguments(classElement.allTypeArguments)

        if (classElement.isAbstract) {
            KotlinIntroductionInterfaceVisitor(classElement, classElement, aopProxyWriter).visit()
        }
        for (interfaceType in interfaceTypes) {
            KotlinIntroductionInterfaceVisitor(interfaceType, classElement, aopProxyWriter).visit()
        }

        return aopProxyWriter
    }

    private fun createClassProxyWriter(beanDefinitionVisitor: BeanDefinitionVisitor) {
        if (classElement.isFinal) {
            visitorContext.fail("Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement.name, classElement);
            return
        }
        aopProxyWriter = createProxyWriter(classElement, beanDefinitionVisitor)
        visitConstructor(aopProxyWriter!!, classElement)
    }

    private fun createProxyWriter(annotationMetadata: AnnotationMetadata,
                                  beanDefinitionVisitor: BeanDefinitionVisitor,
                                  isFactoryType: Boolean = false): AopProxyWriter {
        val interceptorTypeReferences = InterceptedMethodUtil
                .resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND)

        var aopSettings = annotationMetadata.getValues(AnnotationUtil.ANN_AROUND, Boolean::class.java)
        if (isFactoryType && !aopSettings.get(Interceptor.PROXY_TARGET).orElse(true)) {
            val finalSettings: MutableMap<CharSequence, Boolean> = LinkedHashMap()
            for (setting in aopSettings) {
                val entry = aopSettings[setting]
                entry.ifPresent {
                    finalSettings[setting] = it
                }
            }
            finalSettings[Interceptor.PROXY_TARGET] = true
            aopSettings = OptionalValues.of(Boolean::class.java, finalSettings)
        }

        if (beanDefinitionVisitor !is BeanDefinitionWriter) {
            throw IllegalStateException("Internal Error: bean writer not an instance of BeanDefinitionWriter")
        }
        val aopProxyWriter = AopProxyWriter(
            beanDefinitionVisitor,
            aopSettings,
            configurationMetadataBuilder,
            visitorContext,
            *interceptorTypeReferences)

        val beanDefinitionName = beanDefinitionVisitor.beanDefinitionName
        if (isFactoryType) {
            aopProxyWriter.visitSuperBeanDefinitionFactory(beanDefinitionName)
        } else {
            aopProxyWriter.visitSuperBeanDefinition(beanDefinitionName)
        }
        beanDefinitionWriters.add(aopProxyWriter)
        return aopProxyWriter
    }
}
