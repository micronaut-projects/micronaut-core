package io.micronaut.kotlin.processing.beans

import io.micronaut.aop.Adapter
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
import io.micronaut.inject.processing.JavaModelUtils
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
    private val adaptedMethodIndex = AtomicInteger(0)
    private val readPrefixes: Array<String>
    private val writePrefixes: Array<String>

    init {
        this.isAopProxyType = isAopProxyType(classElement)
        this.isExecutableType = isAopProxyType || classElement.hasStereotype(Executable::class.java)
        this.isConfigurationProperties = classElement.hasDeclaredStereotype(ConfigurationReader::class.java)
        if (isConfigurationProperties) {
            this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                classElement,
                classElement.documentation.orElse(null)
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
                classElement.primaryConstructor.filter { it.hasStereotype(AnnotationUtil.INJECT) }.isPresent ||
                classElement.hasStereotype(AnnotationUtil.ANN_INTRODUCTION)
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
        val IS_CONSTRAINT: (AnnotationMetadata) -> Boolean = { am: AnnotationMetadata ->
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

        fun isExecutable(methodElement: MethodElement, classElement: ClassElement): Boolean {
            if (methodElement.hasStereotype(Executable::class.java)) {
                return true
            }
            if (isExecutableType(classElement)) {
                if (classElement == methodElement.declaringType || methodElement.isPublic) {
                    return true
                }
            }
            return false
        }

        private fun isAopProxyType(classElement: ClassElement): Boolean {
            return hasAroundStereotype(classElement.annotationMetadata) &&
                    !classElement.isAbstract &&
                    !classElement.isAssignable(Interceptor::class.java)
        }

        private fun isExecutableType(classElement: ClassElement): Boolean {
            return isAopProxyType(classElement) || classElement.hasStereotype(Executable::class.java)
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
        val inheritedClassMetadata = AnnotationMetadataHierarchy(classElement.annotationMetadata, methodElement.annotationMetadata)

        if (isFactoryClass && methodElement !is ConstructorElement && methodElement.hasDeclaredStereotype(Bean::class.qualifiedName, AnnotationUtil.SCOPE)) {
            visitFactoryMethod(methodElement)
        } else if (isDeclaredBean && methodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
            beanWriter!!.visitPostConstructMethod(
                methodElement.declaringType,
                methodElement.withNewMetadata(inheritedClassMetadata),
                false,
                visitorContext
            )
        } else if (isDeclaredBean && methodElement.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)) {
            beanWriter!!.visitPreDestroyMethod(
                methodElement.declaringType,
                methodElement.withNewMetadata(inheritedClassMetadata),
                false,
                visitorContext
            )
        } else if (methodElement.hasStereotype(AnnotationUtil.INJECT) || methodElement.hasDeclaredStereotype(ConfigurationInject::class.java)) {
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
            val isAdapted = methodElement.hasStereotype(Adapter::class.java)

            if (isAdapted || isExecutable || (isDeclaredBean && hasConstraints)) {

                if (methodElement.isPrivate) {
                    if (methodElement.hasDeclaredStereotype(Executable::class.java) ||
                        hasAroundStereotype(methodElement, true)
                    ) {
                        visitorContext.fail(
                            "Method annotated as executable but is declared private. Change the method to be non-private in order for AOP advice to be applied.",
                            methodElement
                        )
                        return
                    }
                }

                val preprocess = methodElement.booleanValue(Executable::class.java, "processOnStartup").orElse(false)
                if (preprocess) {
                    beanWriter!!.setRequiresMethodProcessing(true)
                }

                val declaredAround =
                    hasAround && !classElement.isAbstract && !classElement.isAssignable(Interceptor::class.java)
                val inheritedAround =
                    isAopProxyType && methodElement.isVisibleInPackage && !declaredAround

                if (inheritedAround || declaredAround) {
                    visitAroundMethod(methodElement, inheritedAround)
                }

                if (isAdapted) {
                    visitAdaptedMethod(methodElement)
                }

                if (!isAdapted && !inheritedAround && !declaredAround) {
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

    private fun visitAroundMethod(methodElement: MethodElement, inheritedAround: Boolean) {
        if (methodElement.isFinal) {
            if (hasAroundStereotype(methodElement, true)) {
                visitorContext.fail(
                    "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.",
                    methodElement
                )
            } else {
                if (inheritedAround && methodElement.declaringType != classElement) {
                    return
                }
                visitorContext.fail(
                    "Public method inherits AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.",
                    methodElement
                )
            }
            return
        }

        if (aopProxyWriter == null) {
            createClassProxyWriter(methodElement.annotationMetadata, beanWriter!!)
        }
        val aopMethod = if (inheritedAround) {
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
        val declaringType = methodElement.declaringType
        val parameterElement = methodElement.parameters[0]
        val propertyMetadata = configurationMetadataBuilder.visitProperty(
            classElement,
            declaringType,
            parameterElement.type.name,
            name,
            null,
            null
        )

        annotatedElement.annotate(Property::class.qualifiedName!!) { builder: AnnotationValueBuilder<Property> ->
            builder.member("name", propertyMetadata.path)
        }

        var requiresReflection = true
        if (methodElement.isPublic) {
            requiresReflection = false
        } else if (methodElement.isPackagePrivate || methodElement.isProtected) {
            val declaringPackage: String = declaringType.packageName
            val concretePackage: String = classElement.packageName
            requiresReflection = declaringPackage != concretePackage
        }

        beanWriter!!.visitSetterValue(
            declaringType,
            methodElement,
            requiresReflection,
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

        beanMethodWriter.visitTypeArguments(producedClassElement.allTypeArguments)
        beanMethodWriter.visitBeanFactoryMethod(
            classElement,
            methodElement
        )
        visitPreDestroy(methodElement, producedClassElement, beanMethodWriter)
        if (methodElement.hasStereotype(AnnotationUtil.ANN_AROUND) && !classElement.isAbstract) {
            visitFactoryMethodAroundAdvice(methodElement, beanMethodWriter)
        } else if (methodElement.hasStereotype(Executable::class.java)) {
            visitFactoryExecutable(methodElement.genericReturnType, methodElement, beanMethodWriter)
        }
        if (hasAroundStereotype(classElement)) {
            visitAroundMethod(methodElement, true)
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
        visitPreDestroy(propertyElement, propertyElement.genericType, beanDefinitionWriter)
        if (hasAroundStereotype(propertyElement)) {
            visitFactoryPropertyAroundAdvice(propertyElement)
        } else if (propertyElement.hasStereotype(Executable::class.java)) {
            visitFactoryExecutable(propertyElement.genericType, propertyElement, beanDefinitionWriter)
        }
        beanDefinitionWriters.add(beanDefinitionWriter)
    }

    private fun visitFactoryPropertyAroundAdvice(propertyElement: PropertyElement) {
        if (propertyElement.isPrimitive) {
            visitorContext.fail("Cannot apply AOP advice to primitive beans", propertyElement)
            return
        } else if (propertyElement.isArray) {
            visitorContext.fail("Cannot apply AOP advice to arrays", propertyElement)
            return
        }
    }

    private fun visitFactoryMethodAroundAdvice(methodElement: MethodElement, beanMethodWriter: BeanDefinitionWriter) {
        val producedClassElement = methodElement.genericReturnType
        if (producedClassElement.isPrimitive) {
            visitorContext.fail("Cannot apply AOP advice to primitive beans", methodElement)
            return
        } else if (producedClassElement.isArray) {
            visitorContext.fail("Cannot apply AOP advice to arrays", methodElement)
            return
        }
        if (producedClassElement.isFinal) {
            visitorContext.fail("Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + producedClassElement.name, methodElement)
            return
        }
        val constructor = producedClassElement.primaryConstructor.orElse(null)
        if (!producedClassElement.isInterface && constructor != null && constructor.parameters.isNotEmpty()) {
            val proxyTargetMode: String =
                methodElement.stringValue(AnnotationUtil.ANN_AROUND, "proxyTargetMode")
                    .orElseGet {
                        // temporary workaround until micronaut-test can be upgraded to 3.0
                        if (methodElement.hasAnnotation("io.micronaut.test.annotation.MockBean")) {
                            "WARN"
                        } else {
                            "ERROR"
                        }
                    }
            when (proxyTargetMode) {
                "ALLOW" -> allowProxyConstruction(constructor)
                "WARN" -> {
                    allowProxyConstruction(constructor)
                    visitorContext.warn("The produced type of a @Factory method has constructor arguments and is proxied. This can lead to unexpected behaviour. See the javadoc for Around.ProxyTargetConstructorMode for more information: $methodElement", methodElement)
                }
                "ERROR" -> {
                    visitorContext.fail(
                        "The produced type from a factory which has AOP proxy advice specified must define an accessible no arguments constructor. Proxying types with constructor arguments can lead to unexpected behaviour. See the javadoc for for Around.ProxyTargetConstructorMode for more information and possible solutions: $methodElement", methodElement)
                    return
                }
                else -> {
                    visitorContext.fail(
                        "The produced type from a factory which has AOP proxy advice specified must define an accessible no arguments constructor. Proxying types with constructor arguments can lead to unexpected behaviour. See the javadoc for for Around.ProxyTargetConstructorMode for more information and possible solutions: $methodElement", methodElement)
                    return
                }
            }
        }
        val proxyWriter = createProxyWriter(methodElement, beanMethodWriter,true)
        visitConstructor(proxyWriter, producedClassElement)
        proxyWriter.visitTypeArguments(producedClassElement.allTypeArguments)

        val newMetadata: AnnotationMetadata = AnnotationMetadataReference(
            beanMethodWriter.beanDefinitionName + BeanDefinitionReferenceWriter.REF_SUFFIX,
            methodElement.annotationMetadata
        )

        producedClassElement.getEnclosedElements(
            ElementQuery.ALL_METHODS
                .filter {
                    it.isPublic && !it.isFinal
                })
            .forEach { beanMethod ->
                proxyWriter.visitAroundMethod(
                    beanMethod.declaringType,
                    beanMethod.withNewMetadata(newMetadata)
                )
            }
    }

    private fun visitFactoryExecutable(classElement: ClassElement,
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
            .filter { it.isPublic && !it.isFinal })
            .forEach { method ->
                beanMethodWriter.visitExecutableMethod(
                    method.declaringType,
                    method.withNewMetadata(newMetadata),
                    visitorContext
                )
            }
    }

    private fun allowProxyConstruction(constructor: MethodElement) {
        val parameters = constructor.parameters
        for (parameter in parameters) {
            if (parameter.isPrimitive && !parameter.isArray) {
                val name = parameter.type.name
                if ("boolean" == name) {
                    parameter.annotate(Value::class.java) { builder: AnnotationValueBuilder<Value?> ->
                        builder.value(false)
                    }
                } else {
                    parameter.annotate(Value::class.java) { builder: AnnotationValueBuilder<Value?> ->
                        builder.value(0)
                    }
                }
            } else {
                // allow null
                parameter.annotate(AnnotationUtil.NULLABLE)
                parameter.removeAnnotation(AnnotationUtil.NON_NULL)
            }
        }
    }

    private fun visitPreDestroy(element: Element, producedType: ClassElement, beanDefinitionWriter: BeanDefinitionWriter) {
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

    private fun visitAdaptedMethod(methodElement: MethodElement) {
        val targetType: Optional<ClassElement> = methodElement.stringValue(Adapter::class.java)
            .flatMap { className -> visitorContext.getClassElement(className) }
            .filter { classElement -> classElement.isInterface }

        if (targetType.isPresent) {
            val typeElement = targetType.get()
            val beanClassName = generateAdaptedMethodClassName(methodElement, typeElement)
            val aopProxyWriter = AopProxyWriter(
                classElement.packageName,
                beanClassName,
                true,
                false,
                methodElement,
                AnnotationMetadataHierarchy(classElement.annotationMetadata, methodElement.annotationMetadata),
                arrayOf(typeElement),
                visitorContext,
                configurationMetadataBuilder,
                null
            )
            aopProxyWriter.visitDefaultConstructor(methodElement.annotationMetadata, visitorContext)
            beanDefinitionWriters.add(aopProxyWriter)

            val methodElements = typeElement.getEnclosedElements(
                ElementQuery.of(MethodElement::class.java)
                    .onlyAccessible()
                    .onlyAbstract()
            )

            if (methodElements.size > 1) {
                visitorContext.fail("Interface to adapt [${typeElement.name}] is not a SAM type. More than one abstract method declared.", methodElement)
                return
            }

            val targetMethod = methodElements.firstOrNull()

            if (targetMethod == null) {
                visitorContext.fail("Interface to adapt [${typeElement.name}] is not a SAM type. There are no abstract methods declared.", methodElement)
                return
            }

            if (methodElement.parameters.size != targetMethod.parameters.size) {
                visitorContext.fail("Cannot adapt method [$methodElement] to target method [${targetMethod}]. Argument lengths don't match.", methodElement)
                return
            }

            val generics = mutableMapOf<String, ClassElement>()
            val paramLen = methodElement.parameters.size
            for (i in 0 until paramLen) {
                val sourceParamType = methodElement.parameters[i].genericType
                val targetParamType = targetMethod.parameters[i].genericType

                if (!sourceParamType.isAssignable(targetParamType)) {
                    visitorContext.fail(
                        "Cannot adapt method [" + methodElement + "] to target method [" + targetMethod + "]. Type [" + sourceParamType.name + "] is not a subtype of type [" + targetParamType.name + "] for argument at position " + i, methodElement)
                    return
                }
                val targetParamRealType = targetMethod.parameters[i].type
                if (targetParamRealType.isGenericPlaceholder) {
                    generics[(targetParamRealType as GenericPlaceholderElement).variableName] = sourceParamType
                }
            }

            if (generics.isNotEmpty()) {
                aopProxyWriter.visitTypeArguments(
                    mapOf(typeElement.name to generics)
                )
            }

            methodElement.annotate(
                Adapter::class.java
            ) { builder: AnnotationValueBuilder<Adapter> ->
                val acv: AnnotationClassValue<Any> =
                    AnnotationClassValue<Any>(classElement.name)
                builder.member(Adapter.InternalAttributes.ADAPTED_BEAN, acv)
                builder.member(
                    Adapter.InternalAttributes.ADAPTED_METHOD,
                    methodElement.name
                )
                builder.member(
                    Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES,
                    *methodElement.parameters.map { param -> AnnotationClassValue<Any>(JavaModelUtils.getClassname(param.genericType)) }.toTypedArray()
                )
                val qualifier = classElement.stringValue(AnnotationUtil.NAMED).orElse(null)
                if (qualifier != null && qualifier.isNotEmpty()) {
                    builder.member(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier)
                }
            }

            aopProxyWriter.visitAroundMethod(
                typeElement,
                targetMethod.withNewMetadata(methodElement.annotationMetadata)
            )
            beanWriter!!.visitExecutableMethod(
                methodElement.declaringType,
                methodElement,
                visitorContext
            )
        }
    }

    private fun generateAdaptedMethodClassName(method: MethodElement,
                                               typeElement: ClassElement): String {
        val rootName = classElement.simpleName + '$' + typeElement.simpleName + '$' + method.simpleName
        return rootName + adaptedMethodIndex.incrementAndGet()
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

        val hasIntroduction = classElement.hasStereotype(AnnotationUtil.ANN_INTRODUCTION)

        if (hasIntroduction && classElement.isFinal) {
            visitorContext.fail("Cannot apply introduction advice to a final class", classElement)
            return
        }

        val beanWriter = if (hasIntroduction) {
            createIntroductionAdviceWriter()
        } else {
            val beanDefinitionWriter = BeanDefinitionWriter(classElement, configurationMetadataBuilder, visitorContext)
            if (isAopProxyType) {
                createClassProxyWriter(classElement.annotationMetadata, beanDefinitionWriter)
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
                if (classElement.isInterface) {
                    beanDefinitionVisitor.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA, visitorContext)
                } else {
                    visitorContext.fail(
                        "Class must have at least one non private constructor in order to be a candidate for dependency injection",
                        classElement
                    )
                }
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
            KotlinIntroductionInterfaceVisitor(classElement, classElement, aopProxyWriter, visitorContext, configurationMetadataBuilder).visit()
        }
        for (interfaceType in interfaceTypes) {
            KotlinIntroductionInterfaceVisitor(interfaceType, classElement, aopProxyWriter, visitorContext, configurationMetadataBuilder).visit()
        }

        return aopProxyWriter
    }

    private fun createClassProxyWriter(annotationMetadata: AnnotationMetadata,
                                       beanDefinitionVisitor: BeanDefinitionVisitor) {
        if (classElement.isFinal) {
            visitorContext.fail("Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement.name, classElement);
            return
        }
        aopProxyWriter = if (beanDefinitionVisitor is AopProxyWriter) {
            beanDefinitionVisitor
        } else {
            createProxyWriter(annotationMetadata, beanDefinitionVisitor)
        }
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
            throw IllegalStateException("Internal Error: bean writer not an instance of BeanDefinitionWriter. Actual type [${beanDefinitionVisitor.javaClass.name}]. Current element: [${classElement.name}]")
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
