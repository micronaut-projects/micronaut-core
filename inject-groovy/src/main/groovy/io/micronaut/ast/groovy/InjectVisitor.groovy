package io.micronaut.ast.groovy

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import io.micronaut.aop.Adapter
import io.micronaut.aop.Around
import io.micronaut.aop.Interceptor
import io.micronaut.aop.Introduction
import io.micronaut.aop.writer.AopProxyWriter
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.ast.groovy.utils.AstClassUtils
import io.micronaut.ast.groovy.utils.AstGenericUtils
import io.micronaut.ast.groovy.utils.PublicAbstractMethodVisitor
import io.micronaut.ast.groovy.utils.PublicMethodVisitor
import io.micronaut.ast.groovy.visitor.AbstractGroovyElement
import io.micronaut.ast.groovy.visitor.GroovyClassElement
import io.micronaut.ast.groovy.visitor.GroovyFieldElement
import io.micronaut.ast.groovy.visitor.GroovyMethodElement
import io.micronaut.ast.groovy.visitor.GroovyParameterElement
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.DefaultScope
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Internal
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.util.ArrayUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.annotation.AnnotationMetadataReference
import io.micronaut.inject.annotation.DefaultAnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.configuration.ConfigurationMetadata
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder
import io.micronaut.inject.configuration.PropertyMetadata
import io.micronaut.inject.processing.ProcessedTypes
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.inject.writer.OriginatingElements
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Scope
import java.lang.reflect.Modifier
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

import static org.codehaus.groovy.ast.ClassHelper.makeCached
import static org.codehaus.groovy.ast.tools.GeneralUtils.getGetterName
import static org.codehaus.groovy.ast.tools.GeneralUtils.getSetterName

@CompileStatic
final class InjectVisitor extends ClassCodeVisitorSupport {
    public static final String AROUND_TYPE = "io.micronaut.aop.Around"
    public static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction"
    final SourceUnit sourceUnit
    final ClassNode concreteClass
    final GroovyClassElement concreteClassElement
    AnnotationMetadata concreteClassAnnotationMetadata
    final ClassElement originatingElement
    final boolean isConfigurationProperties
    final boolean isFactoryClass
    final boolean isExecutableType
    final boolean isAopProxyType
    final boolean isDeclaredBean
    final OptionalValues<Boolean> aopSettings
    final ConfigurationMetadataBuilder<ClassNode> configurationMetadataBuilder
    ConfigurationMetadata configurationMetadata

    final Map<AnnotatedNode, BeanDefinitionVisitor> beanDefinitionWriters = [:]
    private BeanDefinitionVisitor beanWriter
    BeanDefinitionVisitor aopProxyWriter
    final AtomicInteger adaptedMethodIndex = new AtomicInteger(0)
    final AtomicInteger factoryMethodIndex = new AtomicInteger(0)
    private final CompilationUnit compilationUnit

    InjectVisitor(SourceUnit sourceUnit, CompilationUnit compilationUnit, ClassNode targetClassNode, ConfigurationMetadataBuilder<ClassNode> configurationMetadataBuilder) {
        this(sourceUnit, compilationUnit, targetClassNode, null, configurationMetadataBuilder)
    }

    InjectVisitor(SourceUnit sourceUnit, CompilationUnit compilationUnit, ClassNode targetClassNode, Boolean configurationProperties, ConfigurationMetadataBuilder<ClassNode> configurationMetadataBuilder) {
        this.compilationUnit = compilationUnit
        this.sourceUnit = sourceUnit
        this.configurationMetadataBuilder = configurationMetadataBuilder
        this.concreteClass = targetClassNode
        def annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, targetClassNode)
        this.concreteClassAnnotationMetadata = annotationMetadata
        this.originatingElement = AbstractGroovyElement.toClassElement(sourceUnit, compilationUnit, concreteClass, annotationMetadata)
        this.concreteClassElement = originatingElement as GroovyClassElement
        this.isFactoryClass = annotationMetadata.hasStereotype(Factory)
        this.isAopProxyType = annotationMetadata.hasStereotype(AROUND_TYPE) && !targetClassNode.isAbstract()
        this.aopSettings = isAopProxyType ? annotationMetadata.getValues(AROUND_TYPE, Boolean.class) : OptionalValues.<Boolean> empty()
        this.isExecutableType = isAopProxyType || annotationMetadata.hasStereotype(Executable)
        this.isConfigurationProperties = configurationProperties != null ? configurationProperties : annotationMetadata.hasDeclaredStereotype(ConfigurationReader)
        if (isConfigurationProperties) {
            this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                    concreteClass,
                    null
            )
        }

        if (isAopProxyType && Modifier.isFinal(targetClassNode.modifiers)) {
            addError("Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + targetClassNode.name, targetClassNode)
        } else if (isFactoryClass || isConfigurationProperties || annotationMetadata.hasStereotype(Bean, Scope)) {
            defineBeanDefinition(concreteClass)
        }
        this.isDeclaredBean = isExecutableType || isConfigurationProperties || isFactoryClass || annotationMetadata.hasStereotype(Scope.class) || annotationMetadata.hasStereotype(DefaultScope.class) || concreteClass.declaredConstructors.any {
            AnnotationMetadata constructorMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, it)
            constructorMetadata.hasStereotype(Inject)
        }
    }

    BeanDefinitionVisitor getBeanWriter() {
        if (this.beanWriter == null) {
            defineBeanDefinition(concreteClass)
        }
        return beanWriter
    }

    @Override
    void addError(String msg, ASTNode expr) {
        SourceUnit source = getSourceUnit()
        source.getErrorCollector().addError(
                new SyntaxErrorMessage(new SyntaxException(msg + '\n', expr.getLineNumber(), expr.getColumnNumber(), expr.getLastLineNumber(), expr.getLastColumnNumber()), source)
        )
    }

    @Override
    void visitClass(ClassNode node) {
        AnnotationMetadata annotationMetadata
        if (concreteClass == node) {
            annotationMetadata = concreteClassAnnotationMetadata
        } else {
            annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, node)
        }
        boolean isInterface = node.isInterface()
        if (isConfigurationProperties && isInterface) {
            String adviceType = InjectTransform.ANN_CONFIGURATION_ADVICE
            ((Element)concreteClassElement).annotate(adviceType) // hack to make Groovy compile
            concreteClassAnnotationMetadata = concreteClassElement.annotationMetadata
            annotationMetadata = concreteClassAnnotationMetadata
        }
        if (annotationMetadata.hasStereotype(INTRODUCTION_TYPE)) {
            String packageName = node.packageName
            String beanClassName = node.nameWithoutPackage

            String[] aroundInterceptors = annotationMetadata
                    .getAnnotationNamesByStereotype(AROUND_TYPE) as String[]

            String[] introductionInterceptors = annotationMetadata
                    .getAnnotationNamesByStereotype(Introduction.class) as String[]


            String[] interceptorTypes = ArrayUtils.concat(aroundInterceptors, introductionInterceptors)
            String[] interfaceTypes = annotationMetadata.getValue(Introduction.class, "interfaces", String[].class).orElse(new String[0])


            AopProxyWriter aopProxyWriter = new AopProxyWriter(
                    packageName,
                    beanClassName,
                    isInterface,
                    originatingElement,
                    annotationMetadata,
                    interfaceTypes,
                    configurationMetadataBuilder,
                    interceptorTypes
            )
            GroovyClassElement groovyClassElement = new GroovyClassElement(
                    sourceUnit,
                    compilationUnit,
                    node,
                    annotationMetadata
            )
            populateProxyWriterConstructor(groovyClassElement, aopProxyWriter)
            beanDefinitionWriters.put(node, aopProxyWriter)
            visitIntroductionTypePublicMethods(aopProxyWriter, node)
            if (ArrayUtils.isNotEmpty(interfaceTypes)) {
                List<AnnotationNode> annotationNodes = node.annotations
                Set<ClassNode> interfacesToVisit = []

                populateIntroducedInterfaces(annotationNodes, interfacesToVisit)

                if (!interfacesToVisit.isEmpty()) {
                    for (ClassNode itce in interfacesToVisit as Set<ClassNode>) {
                        visitIntroductionTypePublicMethods(aopProxyWriter, itce)
                    }
                }
            }

            if (!isInterface) {
                node.visitContents(this)
            }
        } else {
            boolean isOwningClass = node == concreteClass
            if (isOwningClass && concreteClass.abstract && !isDeclaredBean) {
                return
            }
            ClassNode superClass = node.getSuperClass()
            List<ClassNode> superClasses = []
            while (superClass != null) {
                superClasses.add(superClass)
                superClass = superClass.getSuperClass()
            }
            superClasses = superClasses.reverse()
            for (classNode in superClasses) {
                if (classNode.name != ClassHelper.OBJECT_TYPE.name && classNode.name != GroovyObjectSupport.name && classNode.name != Script.name) {
                    classNode.visitContents(this)
                }
            }
            super.visitClass(node)
        }
    }

    private void populateIntroducedInterfaces(List<AnnotationNode> annotationNodes, Set<ClassNode> interfacesToVisit) {
        for (ann in annotationNodes) {
            if (ann.classNode.name == Introduction.class.getName()) {
                Expression expression = ann.getMember("interfaces")
                if (expression instanceof ClassExpression) {
                    interfacesToVisit.add(((ClassExpression) expression).type)
                } else if (expression instanceof ListExpression) {
                    ListExpression list = (ListExpression) expression
                    for (expr in list.expressions) {
                        if (expr instanceof ClassExpression) {
                            interfacesToVisit.add(((ClassExpression) expr).type)
                        }
                    }
                }
            } else if (AstAnnotationUtils.hasStereotype(sourceUnit, compilationUnit, ann.classNode, Introduction)) {
                populateIntroducedInterfaces(ann.classNode.annotations, interfacesToVisit)
            }
        }
    }

    @CompileStatic
    protected void visitIntroductionTypePublicMethods(AopProxyWriter aopProxyWriter, ClassNode node) {
        AnnotationMetadata typeAnnotationMetadata = aopProxyWriter.getAnnotationMetadata()
        SourceUnit source = this.sourceUnit
        CompilationUnit unit = this.compilationUnit
        GroovyClassElement concreteClassElement = this.concreteClassElement
        AnnotationMetadata concreteClassAnnotationMetadata = this.concreteClassAnnotationMetadata
        PublicMethodVisitor publicMethodVisitor = new PublicAbstractMethodVisitor(source, unit) {

            @Override
            protected boolean isAcceptableMethod(MethodNode methodNode) {
                return super.isAcceptableMethod(methodNode) || AstAnnotationUtils.getAnnotationMetadata(source, unit, methodNode).hasDeclaredStereotype(AROUND_TYPE)
            }

            @Override
            void accept(ClassNode classNode, MethodNode methodNode) {
                Map<String, ParameterElement> targetMethodParamsToType = [:]
                Map<String, ClassElement> targetGenericParams = [:]
                Map<String, AnnotationMetadata> targetAnnotationMetadata = [:]

                AnnotationMetadata annotationMetadata
                if (AstAnnotationUtils.isAnnotated(node.name, methodNode) || AstAnnotationUtils.hasAnnotation(methodNode, Override)) {
                    annotationMetadata = AstAnnotationUtils.newBuilder(source, unit).buildForParent(node.name, node, methodNode)
                } else {
                    annotationMetadata = new AnnotationMetadataReference(
                            aopProxyWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                            typeAnnotationMetadata
                    )
                }
                GroovyMethodElement groovyMethodElement = new GroovyMethodElement(
                        concreteClassElement,
                        source,
                        unit,
                        methodNode,
                        annotationMetadata
                )

                ClassNode owningType = AstGenericUtils.resolveTypeReference(methodNode.declaringClass)
                GroovyClassElement owningClassElement = new GroovyClassElement(
                        source,
                        unit,
                        owningType,
                        concreteClassAnnotationMetadata
                )

                populateParameterData (
                        groovyMethodElement,
                        targetMethodParamsToType,
                        targetGenericParams,
                        targetAnnotationMetadata,
                        false
                )

                if (!annotationMetadata.hasStereotype("io.micronaut.validation.Validated") &&
                        isDeclaredBean) {
                    boolean hasConstraint
                    for (ParameterElement p: groovyMethodElement.getParameters()) {
                        AnnotationMetadata parameterMetadata = p.annotationMetadata
                        if (InjectTransform.IS_CONSTRAINT.test(parameterMetadata)) {
                            hasConstraint = true
                            break
                        }
                    }
                    if (hasConstraint) {
                        if (annotationMetadata instanceof AnnotationMetadataReference) {
                            annotationMetadata = AstAnnotationUtils.newBuilder(source, unit).buildForParent(node.name, node, methodNode)
                            groovyMethodElement = new GroovyMethodElement(
                                    concreteClassElement,
                                    source,
                                    unit,
                                    methodNode,
                                    annotationMetadata
                            )
                        }

                        annotationMetadata = addValidated(groovyMethodElement)
                    }
                }

                if (isConfigurationProperties && methodNode.isAbstract()) {
                    if (!aopProxyWriter.isValidated()) {
                        aopProxyWriter.setValidated(InjectTransform.IS_CONSTRAINT.test(annotationMetadata))
                    }

                    if (!NameUtils.isGetterName(methodNode.name)) {
                        error("Only getter methods are allowed on @ConfigurationProperties interfaces: " + methodNode.name, classNode)
                        return
                    }

                    if (targetMethodParamsToType) {
                        error("Only zero argument getter methods are allowed on @ConfigurationProperties interfaces: " + methodNode.name, classNode)
                        return
                    }
                    String propertyName = NameUtils.getPropertyNameForGetter(methodNode.name)
                    String propertyType = methodNode.returnType.name

                    if ("void".equals(propertyType)) {
                        error("Getter methods must return a value @ConfigurationProperties interfaces: " + methodNode.name, classNode)
                        return
                    }

                    final PropertyMetadata propertyMetadata = configurationMetadataBuilder.visitProperty(
                            current.isInterface() ? current : classNode,
                            classNode,
                            propertyType,
                            propertyName,
                            null,
                            annotationMetadata.stringValue(Bindable.class, "defaultValue").orElse(null)
                    )

                    annotationMetadata = addPropertyMetadata(
                            groovyMethodElement,
                            propertyMetadata
                    )

                    final ClassNode typeElement = !ClassUtils.isJavaBasicType(propertyType) ? methodNode.returnType : null
                    if (typeElement != null && AstAnnotationUtils.hasStereotype(source, unit, typeElement, Scope.class)) {
                        annotationMetadata = addBeanConfigAdvise(annotationMetadata)
                    } else {
                        annotationMetadata = addAnnotation(groovyMethodElement, InjectTransform.ANN_CONFIGURATION_ADVICE)
                    }

                }

                if (AstAnnotationUtils.hasStereotype(source, unit, methodNode, AROUND_TYPE)) {
                    Object[] interceptorTypeReferences = annotationMetadata.getAnnotationNamesByStereotype(Around).toArray()
                    aopProxyWriter.visitInterceptorTypes(interceptorTypeReferences)
                }

                if (methodNode.isAbstract()) {
                    aopProxyWriter.visitIntroductionMethod(
                            owningClassElement,
                            groovyMethodElement
                    )
                } else {
                    aopProxyWriter.visitAroundMethod(
                            owningClassElement,
                            groovyMethodElement
                    )
                }
            }

            @CompileDynamic
            private void error(String msg, ClassNode classNode) {
                addError(msg, (ASTNode) classNode)
            }

            @CompileDynamic
            private AnnotationMetadata addBeanConfigAdvise(AnnotationMetadata annotationMetadata) {
                new GroovyAnnotationMetadataBuilder(source, compilationUnit).annotate(
                        annotationMetadata,
                        AnnotationValue.builder(InjectTransform.ANN_CONFIGURATION_ADVICE).member("bean", true).build()
                )
            }

        }
        publicMethodVisitor.accept(node)
    }

    @Override
    protected void visitConstructorOrMethod(MethodNode methodNode, boolean isConstructor) {
        if (methodNode.isSynthetic() || methodNode.name.contains('$')) return

        String methodName = methodNode.name
        ClassNode declaringClass = methodNode.declaringClass
        AnnotationMetadata methodAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, methodNode)
        def declaringElement = new GroovyClassElement(
                sourceUnit,
                compilationUnit,
                declaringClass,
                AnnotationMetadata.EMPTY_METADATA
        )
        if (isFactoryClass && !isConstructor && methodAnnotationMetadata.hasDeclaredStereotype(Bean, Scope)) {
            methodAnnotationMetadata = new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit).buildForParent(methodNode.returnType, methodNode, true)
            if (concreteClassAnnotationMetadata.hasDeclaredStereotype(Around)) {
                visitExecutableMethod(declaringClass, methodNode, methodAnnotationMetadata, methodName, methodNode.isPublic())
            }

            GroovyMethodElement factoryMethodElement = new GroovyMethodElement(
                    concreteClassElement,
                    sourceUnit,
                    compilationUnit,
                    methodNode,
                    methodAnnotationMetadata
            )
            ClassElement producedClassElement = factoryMethodElement.genericReturnType
            String beanDefinitionPackage = concreteClass.packageName
            String upperCaseMethodName = NameUtils.capitalize(methodNode.getName())
            String factoryMethodBeanDefinitionName =
                    beanDefinitionPackage + '.$' + concreteClass.nameWithoutPackage + '$' + upperCaseMethodName + factoryMethodIndex.getAndIncrement() + "Definition"

            BeanDefinitionWriter beanMethodWriter = new BeanDefinitionWriter(
                    producedClassElement.packageName,
                    producedClassElement.simpleName,
                    factoryMethodBeanDefinitionName,
                    producedClassElement.name,
                    producedClassElement.isInterface(),
                    OriginatingElements.of(originatingElement),
                    methodAnnotationMetadata,
                    configurationMetadataBuilder
            )

            ClassNode returnType = methodNode.getReturnType()
            Map<String, ClassNode> genericsSpec = AstGenericUtils.createGenericsSpec(returnType)
            if (genericsSpec) {
                Map<String, Object> boundTypes = [:]
                GenericsType[] genericsTypes = returnType.redirect().getGenericsTypes()
                Map<String, Map<String, Object>> typeArguments = [:]
                for (gt in genericsTypes) {
                    ClassNode cn = genericsSpec[gt.name]
                    boundTypes.put(gt.name, AstGenericUtils.resolveTypeReference(cn))
                }

                typeArguments.put(AstGenericUtils.resolveTypeReference(returnType).getName(), boundTypes)
                AstGenericUtils.populateTypeArguments(returnType, typeArguments)
                beanMethodWriter.visitTypeArguments(finalizeTypeArguments(typeArguments))
            }

            Map<String, ParameterElement> paramsToType = [:]
            Map<String, ClassElement> paramsGenerics = [:]
            Map<String, AnnotationMetadata> argumentAnnotationMetadata = [:]
            populateParameterData(
                    factoryMethodElement,
                    paramsToType,
                    paramsGenerics,
                    argumentAnnotationMetadata,
                    false
            )

            beanMethodWriter.visitBeanFactoryMethod(
                    originatingElement,
                    factoryMethodElement
            )

            if (methodAnnotationMetadata.hasStereotype(AROUND_TYPE)) {

                if (Modifier.isFinal(returnType.modifiers)) {
                    addError(
                            "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: $methodNode",
                            methodNode
                    )
                    return
                }

                Object[] interceptorTypeReferences = methodAnnotationMetadata.getAnnotationNamesByStereotype(Around).toArray()
                OptionalValues<Boolean> aopSettings = methodAnnotationMetadata.getValues(AROUND_TYPE, Boolean)
                Map<CharSequence, Object> finalSettings = [:]
                for (key in aopSettings) {
                    finalSettings.put(key, aopSettings.get(key).get())
                }
                finalSettings.put(Interceptor.PROXY_TARGET, true)

                AopProxyWriter proxyWriter = new AopProxyWriter(
                        beanMethodWriter,
                        OptionalValues.of(Boolean.class, finalSettings),
                        configurationMetadataBuilder,
                        interceptorTypeReferences
                )
                if (producedClassElement.isInterface()) {
                    proxyWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA)
                } else {
                    populateProxyWriterConstructor(producedClassElement, proxyWriter)
                }
                SourceUnit source = this.sourceUnit
                CompilationUnit unit = this.compilationUnit
                GroovyClassElement finalConcreteClassElement = this.concreteClassElement
                new PublicMethodVisitor(source) {
                    @Override
                    void accept(ClassNode classNode, MethodNode targetBeanMethodNode) {
                        AnnotationMetadata annotationMetadata
                        if (AstAnnotationUtils.isAnnotated(producedClassElement.name, methodNode)) {
                            annotationMetadata = AstAnnotationUtils.newBuilder(source, unit)
                                    .buildForParent(producedClassElement.name, methodNode, targetBeanMethodNode)
                        } else {
                            annotationMetadata = new AnnotationMetadataReference(
                                    beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    methodAnnotationMetadata
                            )
                        }
                        GroovyMethodElement targetMethodElement = new GroovyMethodElement(
                                finalConcreteClassElement,
                                source,
                                unit,
                                targetBeanMethodNode,
                                annotationMetadata
                        )

                        proxyWriter.visitAroundMethod(
                                targetMethodElement.declaringType,
                                targetMethodElement
                        )
                    }
                }.accept(returnType)
                beanDefinitionWriters.put(new AnnotatedNode(), proxyWriter)

            }
            Optional<String> preDestroy = methodAnnotationMetadata.getValue(Bean, "preDestroy", String.class)
            if (preDestroy.isPresent()) {
                String destroyMethodName = preDestroy.get()
                MethodNode destroyMethod = ((ClassNode) producedClassElement.nativeType).getMethod(destroyMethodName)
                if (destroyMethod != null) {
                    def destroyMethodElement = new GroovyMethodElement(
                            producedClassElement as GroovyClassElement,
                            sourceUnit,
                            compilationUnit,
                            destroyMethod,
                            AnnotationMetadata.EMPTY_METADATA
                    )
                    beanMethodWriter.visitPreDestroyMethod(
                            producedClassElement as GroovyClassElement,
                            destroyMethodElement,
                            false
                    )
                } else {
                    addError("@Bean method defines a preDestroy method that does not exist or is not public: $destroyMethodName", methodNode )
                }
            }
            beanDefinitionWriters.put(methodNode, beanMethodWriter)
        } else if (methodAnnotationMetadata.hasStereotype(Inject.name, ProcessedTypes.POST_CONSTRUCT, ProcessedTypes.PRE_DESTROY)) {
            if (isConstructor && methodAnnotationMetadata.hasStereotype(Inject)) {
                // constructor with explicit @Inject
                defineBeanDefinition(concreteClass)
            } else if (!isConstructor) {
                if (!methodNode.isStatic() && !methodNode.isAbstract()) {
                    boolean isParent = declaringClass != concreteClass
                    MethodNode overriddenMethod = isParent ? concreteClass.getMethod(methodName, methodNode.parameters) : methodNode
                    boolean overridden = isParent && overriddenMethod.declaringClass != declaringClass

                    boolean isPackagePrivate = isPackagePrivate(methodNode, methodNode.modifiers)
                    boolean isPrivate = methodNode.isPrivate()

                    if (isParent && !isPrivate && !isPackagePrivate) {

                        if (overridden) {
                            // bail out if the method has been overridden, since it will have already been handled
                            return
                        }
                    }
                    boolean packagesDiffer = overriddenMethod.declaringClass.packageName != declaringClass.packageName
                    boolean isPackagePrivateAndPackagesDiffer = overridden && packagesDiffer && isPackagePrivate
                    boolean requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer
                    boolean overriddenInjected = overridden && AstAnnotationUtils.hasStereotype(sourceUnit, compilationUnit, overriddenMethod, Inject)

                    if (isParent && isPackagePrivate && !isPackagePrivateAndPackagesDiffer && overriddenInjected) {
                        // bail out if the method has been overridden by another method annotated with @INject
                        return
                    }
                    if (isParent && overridden && !overriddenInjected && !isPackagePrivateAndPackagesDiffer && !isPrivate) {
                        // bail out if the overridden method is package private and in the same package
                        // and is not annotated with @Inject
                        return
                    }
                    if (!requiresReflection && isInheritedAndNotPublic(methodNode, declaringClass, methodNode.modifiers)) {
                        requiresReflection = true
                    }

                    GroovyMethodElement groovyMethodElement = new GroovyMethodElement(
                            declaringElement,
                            sourceUnit,
                            compilationUnit,
                            methodNode,
                            methodAnnotationMetadata
                    )
                    Map<String, ParameterElement> paramsToType = [:]
                    Map<String, AnnotationMetadata> argumentAnnotationMetadata = [:]
                    Map<String, ClassElement> genericTypeMap = [:]
                    populateParameterData(
                            groovyMethodElement,
                            paramsToType,
                            genericTypeMap,
                            argumentAnnotationMetadata,
                            false
                    )

                    if (isDeclaredBean && methodAnnotationMetadata.hasStereotype(ProcessedTypes.POST_CONSTRUCT)) {
                        defineBeanDefinition(concreteClass)
                        def beanWriter = getBeanWriter()
                        if (aopProxyWriter instanceof AopProxyWriter && !((AopProxyWriter)aopProxyWriter).isProxyTarget()) {
                            beanWriter = aopProxyWriter
                        }
                        beanWriter.visitPostConstructMethod(
                                declaringElement,
                                groovyMethodElement,
                                requiresReflection
                        )
                    } else if (isDeclaredBean && methodAnnotationMetadata.hasStereotype(ProcessedTypes.PRE_DESTROY)) {
                        defineBeanDefinition(concreteClass)
                        def beanWriter = getBeanWriter()
                        if (aopProxyWriter instanceof AopProxyWriter && !((AopProxyWriter)aopProxyWriter).isProxyTarget()) {
                            beanWriter = aopProxyWriter
                        }
                        beanWriter.visitPreDestroyMethod(
                                declaringElement,
                                groovyMethodElement,
                                requiresReflection
                        )
                    } else if (methodAnnotationMetadata.hasStereotype(Inject.class)) {
                        defineBeanDefinition(concreteClass)
                        getBeanWriter().visitMethodInjectionPoint(
                                declaringElement,
                                groovyMethodElement,
                                requiresReflection
                        )
                    }
                }
            }
        } else if (!isConstructor) {
            boolean hasInvalidModifiers = methodNode.isStatic() || methodNode.isAbstract() || methodNode.isSynthetic() || methodAnnotationMetadata.hasAnnotation(Internal) || methodNode.isPrivate()
            boolean isPublic = methodNode.isPublic() && !hasInvalidModifiers
            boolean isExecutable = ((isExecutableType && isPublic) || methodAnnotationMetadata.hasStereotype(Executable) || methodAnnotationMetadata.hasStereotype(AROUND_TYPE)) && !hasInvalidModifiers
            if (isDeclaredBean && isExecutable) {
                visitExecutableMethod(declaringClass, methodNode, methodAnnotationMetadata, methodName, isPublic)
            } else if (isConfigurationProperties && isPublic) {
                if (NameUtils.isSetterName(methodNode.name) && methodNode.parameters.length == 1) {
                    String propertyName = NameUtils.getPropertyNameForSetter(methodNode.name)
                    GroovyMethodElement groovyMethodElement = new GroovyMethodElement(
                            declaringElement,
                            sourceUnit,
                            compilationUnit,
                            methodNode,
                            methodAnnotationMetadata
                    )
                    ParameterElement parameterElement = groovyMethodElement.parameters[0]

                    if (methodAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                        getBeanWriter().visitConfigBuilderMethod(
                                parameterElement.type,
                                NameUtils.getterNameFor(propertyName),
                                methodAnnotationMetadata,
                                configurationMetadataBuilder,
                                parameterElement.type.interface
                        )
                        try {
                            visitConfigurationBuilder(
                                    declaringElement,
                                    methodAnnotationMetadata,
                                    parameterElement.type,
                                    getBeanWriter()
                            )
                        } finally {
                            getBeanWriter().visitConfigBuilderEnd()
                        }
                    } else if (declaringClass.getField(propertyName) == null) {
                        if (shouldExclude(configurationMetadata, propertyName)) {
                            return
                        }
                        PropertyMetadata propertyMetadata = configurationMetadataBuilder.visitProperty(
                                concreteClass,
                                declaringClass,
                                parameterElement.type.name,
                                propertyName,
                                null,
                                null
                        )

                        methodAnnotationMetadata = addPropertyMetadata(parameterElement, propertyMetadata)

                        getBeanWriter().visitSetterValue(
                                groovyMethodElement.declaringType,
                                groovyMethodElement,
                                false,
                                true
                        )
                    }
                } else if (NameUtils.isGetterName(methodNode.name)) {
                    if (!getBeanWriter().isValidated()) {
                        getBeanWriter().setValidated(InjectTransform.IS_CONSTRAINT.test(methodAnnotationMetadata))
                    }
                }
            } else if (isPublic) {
                def sourceUnit = sourceUnit
                def compilationUnit = this.compilationUnit
                final boolean isConstrained = isDeclaredBean &&
                        methodNode.getParameters()
                                .any { Parameter p ->
                                    AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, p)
                                    InjectTransform.IS_CONSTRAINT.test(annotationMetadata)
                                }
                if (isConstrained) {
                    visitExecutableMethod(declaringClass, methodNode, methodAnnotationMetadata, methodName, isPublic)
                }
            }
        }
    }

    private static AnnotationMetadata addPropertyMetadata(Element element, PropertyMetadata propertyMetadata) {
        element.annotate(
                Property.class.getName(),
                { builder ->
                    builder.member("name", propertyMetadata.path)
                }

        )
        return element.annotationMetadata
    }

    private void visitExecutableMethod(
            ClassNode declaringClass,
            MethodNode methodNode,
            AnnotationMetadata methodAnnotationMetadata,
            String methodName, boolean isPublic) {
        if (declaringClass != ClassHelper.OBJECT_TYPE) {

            boolean isOwningClass = declaringClass == concreteClass
            boolean isParent = declaringClass != concreteClass

            GroovyClassElement declaringElement = new GroovyClassElement(sourceUnit, compilationUnit, declaringClass, concreteClassAnnotationMetadata)
            def methodElement = new GroovyMethodElement(concreteClassElement, sourceUnit, compilationUnit, methodNode, methodAnnotationMetadata)
            Parameter[] resolvedParameters = methodElement.parameters.collect { ParameterElement pe ->
                if (pe.type.isPrimitive()) {
                    return (Parameter) pe.nativeType
                } else {
                    return new Parameter((ClassNode) pe.genericType.nativeType, pe.name)
                }
            } as Parameter[]

            MethodNode overriddenMethod = isParent ? concreteClass.getMethod(methodName, resolvedParameters) : methodNode
            if (!isOwningClass && overriddenMethod != null && overriddenMethod.declaringClass != declaringClass) {
                return
            }

            Map<String, ParameterElement> paramsToType = [:]
            Map<String, ClassElement> genericParams = [:]
            Map<String, AnnotationMetadata> argumentAnnotationMetadata = [:]
            populateParameterData(
                    methodElement,
                    paramsToType,
                    genericParams,
                    argumentAnnotationMetadata,
                    false
            )

            defineBeanDefinition(concreteClass)

            boolean preprocess = methodAnnotationMetadata.booleanValue(Executable.class, "processOnStartup").orElse(false)
            if (preprocess) {
                getBeanWriter().setRequiresMethodProcessing(true)
            }
            final boolean hasConstraints = argumentAnnotationMetadata.values().stream().anyMatch({ am ->
                InjectTransform.IS_CONSTRAINT.test(am)
            })

            if (hasConstraints) {
                if (!methodAnnotationMetadata.hasStereotype("io.micronaut.validation.Validated")) {
                    methodAnnotationMetadata = addValidated(methodElement)
                }
            }

            boolean executorMethodAdded = false

            if (methodAnnotationMetadata.hasStereotype(Adapter.class)) {
                visitAdaptedMethod(methodNode, methodAnnotationMetadata)
            }

            boolean hasAround = hasConstraints || methodAnnotationMetadata.hasStereotype(AROUND_TYPE)
            if ((isAopProxyType && isPublic) || (hasAround && !concreteClass.isAbstract())) {

                boolean hasExplicitAround = methodAnnotationMetadata.hasDeclaredStereotype(AROUND_TYPE)

                if (methodNode.isFinal()) {
                    if (hasExplicitAround) {
                        addError("Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.", methodNode)
                    } else {
                        addError("Public method inherits AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.", methodNode)
                    }
                } else {
                    Object[] interceptorTypeReferences = methodAnnotationMetadata.getAnnotationNamesByStereotype(Around).toArray()
                    if (hasConstraints) {
                        interceptorTypeReferences = ArrayUtils.concat(interceptorTypeReferences, "io.micronaut.validation.Validated")
                    }
                    OptionalValues<Boolean> aopSettings = methodAnnotationMetadata.getValues(AROUND_TYPE, Boolean)
                    AopProxyWriter proxyWriter = resolveProxyWriter(
                            aopSettings,
                            false,
                            interceptorTypeReferences
                    )

                    if (proxyWriter != null && !methodNode.isFinal()) {

                        proxyWriter.visitInterceptorTypes(interceptorTypeReferences)

                        proxyWriter.visitAroundMethod(
                                declaringElement,
                                methodElement
                        )

                        executorMethodAdded = true
                    }
                }
            }

            if (!executorMethodAdded) {
                getBeanWriter().visitExecutableMethod(
                        declaringElement,
                        methodElement
                )
            }
        }
    }

    @CompileDynamic
    private AnnotationMetadata addValidated(Element element) {
        return addAnnotation(element, "io.micronaut.validation.Validated")
    }

    @CompileDynamic
    private AnnotationMetadata addAnnotation(Element element, String annotationName) {
        element.annotate(annotationName)
        return element.annotationMetadata
    }

    private AopProxyWriter resolveProxyWriter(
            OptionalValues<Boolean> aopSettings,
            boolean isFactoryType,
            Object[] interceptorTypeReferences) {
        AopProxyWriter proxyWriter = (AopProxyWriter) aopProxyWriter
        if (proxyWriter == null) {

            proxyWriter = new AopProxyWriter(
                    (BeanDefinitionWriter) getBeanWriter(),
                    aopSettings,
                    configurationMetadataBuilder,
                    interceptorTypeReferences
            )

            populateProxyWriterConstructor(concreteClassElement, proxyWriter)
            String beanDefinitionName = getBeanWriter().getBeanDefinitionName()
            if (isFactoryType) {
                proxyWriter.visitSuperBeanDefinitionFactory(beanDefinitionName)
            } else {
                proxyWriter.visitSuperBeanDefinition(beanDefinitionName)
            }

            this.aopProxyWriter = proxyWriter

            def node = new AnnotatedNode()
            beanDefinitionWriters.put(node, proxyWriter)
        }
        proxyWriter
    }

    protected void populateProxyWriterConstructor(ClassElement targetClass, AopProxyWriter proxyWriter) {
        MethodElement constructor = targetClass.getPrimaryConstructor().orElse(null)
        if (constructor != null) {
            if (constructor.parameters.length == 0) {
                proxyWriter.visitDefaultConstructor(
                        AnnotationMetadata.EMPTY_METADATA
                )
            } else {
                Map<String, ParameterElement> constructorParamsToType = [:]
                Map<String, AnnotationMetadata> constructorArgumentMetadata = [:]
                Map<String, ClassElement> constructorGenericTypeMap = [:]
                populateParameterData(
                        (GroovyMethodElement) constructor,
                        constructorParamsToType,
                        constructorGenericTypeMap,
                        constructorArgumentMetadata,
                        false
                )
                proxyWriter.visitBeanDefinitionConstructor(
                        constructor,
                        constructor.isPrivate()
                )
            }
        } else {
            ClassNode cn = targetClass.nativeType as ClassNode
            if (cn.declaredConstructors.isEmpty()) {
                proxyWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA)
            } else {
                addError("Class must have at least one non private constructor in order to be a candidate for dependency injection", (ASTNode) targetClass.nativeType)
            }
        }
    }

    protected static boolean isPackagePrivate(AnnotatedNode annotatedNode, int modifiers) {
        return ((!Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers) && !Modifier.isPrivate(modifiers)) || !annotatedNode.getAnnotations(makeCached(PackageScope)).isEmpty())
    }

    @Override
    void visitField(FieldNode fieldNode) {
        if (fieldNode.name == 'metaClass') return
        int modifiers = fieldNode.modifiers
        if (Modifier.isStatic(modifiers)) {
            return
        }
        if (fieldNode.isSynthetic() && !isPackagePrivate(fieldNode, fieldNode.modifiers)) {
            return
        }
        ClassNode declaringClass = fieldNode.declaringClass
        AnnotationMetadata fieldAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, fieldNode)
        if (Modifier.isFinal(modifiers) && !fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder)) {
            return
        }
        boolean isInject = fieldAnnotationMetadata.hasStereotype(Inject)
        boolean isValue = isValueInjection(fieldNode, fieldAnnotationMetadata)
        GroovyFieldElement fieldElement = new GroovyFieldElement(sourceUnit, compilationUnit, fieldNode, fieldNode, fieldAnnotationMetadata)

        if ((isInject || isValue) && declaringClass.getProperty(fieldNode.getName()) == null) {
            defineBeanDefinition(concreteClass)
            if (!fieldNode.isStatic()) {

                boolean isPrivate = Modifier.isPrivate(modifiers)
                boolean requiresReflection = isPrivate || isInheritedAndNotPublic(fieldNode, fieldNode.declaringClass, modifiers)
                if (!getBeanWriter().isValidated()) {
                    getBeanWriter().setValidated(InjectTransform.IS_CONSTRAINT.test(fieldAnnotationMetadata))
                }
                String fieldName = fieldNode.name
                ClassElement fieldType = fieldElement.type
                if (isValue) {
                    if (isConfigurationProperties && fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                        if(requiresReflection) {
                            // Using the field would throw a IllegalAccessError, use the method instead
                            String fieldGetterName = NameUtils.getterNameFor(fieldName)
                            MethodNode getterMethod = declaringClass.methods?.find { it.name == fieldGetterName}
                            if(getterMethod != null) {
                                getBeanWriter().visitConfigBuilderMethod(
                                        fieldType,
                                        getterMethod.name,
                                        fieldAnnotationMetadata,
                                        configurationMetadataBuilder,
                                        fieldType.interface
                                )
                            } else {
                                addError("ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.", fieldNode)
                            }
                        } else {
                            getBeanWriter().visitConfigBuilderField(fieldType, fieldName, fieldAnnotationMetadata, configurationMetadataBuilder, fieldNode.type.interface)
                        }
                        try {
                            visitConfigurationBuilder(
                                    fieldElement.declaringType,
                                    fieldAnnotationMetadata,
                                    fieldElement.type, getBeanWriter()
                            )
                        } finally {
                            getBeanWriter().visitConfigBuilderEnd()
                        }
                    } else {
                        if (isConfigurationProperties) {
                            if (shouldExclude(configurationMetadata, fieldName)) {
                                return
                            }
                            PropertyMetadata propertyMetadata = configurationMetadataBuilder.visitProperty(
                                    concreteClass,
                                    declaringClass,
                                    fieldNode.type.name,
                                    fieldName,
                                    null, // TODO: fix groovy doc support
                                    null
                            )
                            fieldElement.annotate(Property.class.getName(), {builder  ->
                                builder.member("name", propertyMetadata.path)
                            })
                        }
                        getBeanWriter().visitFieldValue(
                                fieldElement.declaringType,
                                fieldElement,
                                requiresReflection,
                                isConfigurationProperties
                        )
                    }
                } else {
                    getBeanWriter().visitFieldInjectionPoint(
                            fieldElement.declaringType,
                            fieldElement,
                            requiresReflection
                    )
                }
            }
        }
    }

    @Override
    @CompileDynamic
    void visitProperty(PropertyNode propertyNode) {
        FieldNode fieldNode = propertyNode.field
        if (fieldNode.name == 'metaClass') return
        def modifiers = propertyNode.getModifiers()
        if (Modifier.isStatic(modifiers)) {
            return
        }
        AnnotationMetadata fieldAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, fieldNode)
        if (Modifier.isFinal(modifiers) && !fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder)) {
            return
        }
        boolean isInject = fieldNode != null && fieldAnnotationMetadata.hasStereotype(Inject)
        boolean isValue = isValueInjection(fieldNode, fieldAnnotationMetadata)

        String propertyName = propertyNode.name
        if (!propertyNode.isStatic() && (isInject || isValue)) {
            defineBeanDefinition(concreteClass)
            GroovyFieldElement fieldElement = new GroovyFieldElement(
                    sourceUnit,
                    compilationUnit,
                    fieldNode,
                    fieldNode,
                    fieldAnnotationMetadata
            )

            if (!getBeanWriter().isValidated()) {
                getBeanWriter().setValidated(InjectTransform.IS_CONSTRAINT.test(fieldAnnotationMetadata))
            }

            if (isInject) {
                GroovyParameterElement parameterElement = new GroovyParameterElement(
                        null,
                        sourceUnit,
                        compilationUnit,
                        new Parameter(fieldNode.type, fieldNode.name),
                        fieldAnnotationMetadata
                ) {
                    @Override
                    ClassElement getGenericType() {
                        return fieldElement.genericType
                    }
                }
                MethodElement methodElement = MethodElement.of(
                        fieldElement.declaringType,
                        fieldElement,
                        PrimitiveElement.VOID,
                        PrimitiveElement.VOID,
                        getSetterName(propertyName),
                        parameterElement
                )
                getBeanWriter().visitMethodInjectionPoint(
                        fieldElement.declaringType,
                        methodElement,
                        false
                )
            } else if (isValue) {
                if (isConfigurationProperties && fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                    getBeanWriter().visitConfigBuilderMethod(
                            fieldElement.type,
                            getGetterName(propertyNode),
                            fieldAnnotationMetadata,
                            configurationMetadataBuilder,
                            fieldNode.type.interface)
                    try {
                        visitConfigurationBuilder(
                                fieldElement.declaringType,
                                fieldAnnotationMetadata,
                                fieldElement.type,
                                getBeanWriter()
                        )
                    } finally {
                        getBeanWriter().visitConfigBuilderEnd()
                    }
                } else {
                    if (isConfigurationProperties) {
                        if (shouldExclude(configurationMetadata, propertyName)) {
                            return
                        }
                        PropertyMetadata propertyMetadata = configurationMetadataBuilder.visitProperty(
                                concreteClass,
                                fieldNode.declaringClass,
                                propertyNode.type.name,
                                propertyNode.name,
                                null, // TODO: fix groovy doc support
                                null
                        )
                        fieldElement.annotate(Property.class.getName(), { builder ->
                            builder.member("name", propertyMetadata.path)
                        })
                        fieldAnnotationMetadata = fieldElement.annotationMetadata
                    }
                    def setterName = GeneralUtils.getSetterName(propertyName)

                    GroovyParameterElement parameterElement = new GroovyParameterElement(
                            null,
                            sourceUnit,
                            compilationUnit,
                            new Parameter(fieldNode.type, fieldNode.name),
                            fieldAnnotationMetadata
                    ) {
                        @Override
                        ClassElement getGenericType() {
                            return fieldElement.genericType
                        }
                    }
                    def methodElement = MethodElement.of(
                            fieldElement.declaringType,
                            fieldAnnotationMetadata,
                            PrimitiveElement.VOID,
                            PrimitiveElement.VOID,
                            setterName,
                            parameterElement
                    )
                    getBeanWriter().visitSetterValue(
                            fieldElement.declaringType,
                            methodElement,
                            false,
                            isConfigurationProperties
                    )
                }
            }
        } else if (isAopProxyType && !propertyNode.isStatic()) {
            AopProxyWriter aopWriter = (AopProxyWriter) aopProxyWriter
            if (aopProxyWriter != null) {
                AnnotationMetadata fieldMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, propertyNode.field)
                GroovyFieldElement fieldElement = new GroovyFieldElement(
                        sourceUnit,
                        compilationUnit,
                        fieldNode,
                        fieldNode,
                        fieldMetadata
                )
                GroovyParameterElement parameterElement = new GroovyParameterElement(
                        null,
                        sourceUnit,
                        compilationUnit,
                        new Parameter(fieldNode.type, fieldNode.name),
                        fieldAnnotationMetadata
                ) {
                    @Override
                    ClassElement getGenericType() {
                        return fieldElement.genericType
                    }
                }
                def methodAnnotationMetadata = new AnnotationMetadataHierarchy(
                        concreteClassAnnotationMetadata,
                        fieldAnnotationMetadata
                )
                MethodElement setterElement = MethodElement.of(
                        fieldElement.declaringType,
                        methodAnnotationMetadata,
                        PrimitiveElement.VOID,
                        PrimitiveElement.VOID,
                        getSetterName(propertyName),
                        parameterElement
                )
                aopWriter.visitAroundMethod(
                        fieldElement.declaringType,
                        setterElement
                )

                // also visit getter to ensure proxying

                MethodElement getterElement = MethodElement.of(
                        fieldElement.declaringType,
                        methodAnnotationMetadata,
                        fieldElement.type,
                        fieldElement.genericType,
                        getGetterName(propertyNode)
                )
                aopWriter.visitAroundMethod(
                        fieldElement.declaringType,
                        getterElement
                )
            }
        }
    }

    private boolean isValueInjection(FieldNode fieldNode, AnnotationMetadata fieldAnnotationMetadata) {
        fieldNode != null && (
                fieldAnnotationMetadata.hasStereotype(Value) ||
                        fieldAnnotationMetadata.hasStereotype(Property) ||
                        isConfigurationProperties
        )
    }

    protected boolean isInheritedAndNotPublic(AnnotatedNode annotatedNode, ClassNode declaringClass, int modifiers) {
        return declaringClass != concreteClass &&
                declaringClass.packageName != concreteClass.packageName &&
                ((Modifier.isProtected(modifiers) || !Modifier.isPublic(modifiers)) || !annotatedNode.getAnnotations(makeCached(PackageScope)).isEmpty())
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return sourceUnit
    }

    private void defineBeanDefinition(ClassNode classNode) {
        if (!beanDefinitionWriters.containsKey(classNode)) {
            if (classNode.packageName == null) {
                addError("Micronaut beans cannot be in the default package", classNode)
                return
            }
            AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, classNode)
            if (configurationMetadata != null) {
                String existingPrefix = annotationMetadata.getValue(
                        ConfigurationReader.class,
                        "prefix", String.class)
                        .orElse("")

                def computedPrefix = StringUtils.isNotEmpty(existingPrefix) ? existingPrefix + "." + configurationMetadata.getName() : configurationMetadata.getName()
                annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                        annotationMetadata,
                        ConfigurationReader.class.getName(),
                        "prefix",
                        computedPrefix
                )
            }

            GroovyClassElement groovyClassElement = new GroovyClassElement(
                    sourceUnit,
                    compilationUnit,
                    classNode,
                    annotationMetadata
            )

            if (annotationMetadata.hasStereotype(Singleton)) {
                addError("Class annotated with groovy.lang.Singleton instead of javax.inject.Singleton. Import javax.inject.Singleton to use Micronaut Dependency Injection.", classNode)
            }

            beanWriter = new BeanDefinitionWriter(groovyClassElement, configurationMetadataBuilder)
            visitTypeArguments(classNode, (BeanDefinitionWriter) beanWriter)

            beanDefinitionWriters.put(classNode, beanWriter)

            GroovyMethodElement constructor = groovyClassElement.getPrimaryConstructor().orElse(null) as GroovyMethodElement

            if (constructor != null) {
                if (constructor.parameters.length == 0) {

                    beanWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA)
                } else {
                    def constructorMetadata = constructor.annotationMetadata
                    final boolean isConstructBinding = constructorMetadata.hasDeclaredStereotype(ConfigurationInject.class)
                    if (isConstructBinding) {
                        this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                                concreteClass,
                                null)
                    }
                    beanWriter.visitBeanDefinitionConstructor(constructor, constructor.isPrivate())
                }

            } else {
                ClassNode cn = groovyClassElement.nativeType as ClassNode
                if (cn.declaredConstructors.isEmpty()) {
                    beanWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA)
                } else {
                    addError("Class must have at least one non private constructor in order to be a candidate for dependency injection", classNode)
                }
            }

            if (isAopProxyType) {
                Object[] interceptorTypeReferences = annotationMetadata.getAnnotationNamesByStereotype(Around).toArray()
                resolveProxyWriter(aopSettings, false, interceptorTypeReferences)
            }

        } else {
            beanWriter = beanDefinitionWriters.get(classNode)
        }
    }

    @CompileDynamic
    private void visitAdaptedMethod(MethodNode method, AnnotationMetadata methodAnnotationMetadata) {
        Optional<ClassNode> adaptedType = methodAnnotationMetadata.getValue(Adapter.class, String.class).flatMap({ String s ->
            ClassNode cn = sourceUnit.AST.classes.find { ClassNode cn -> cn.name == s }
            if (cn != null) {
                return Optional.of(cn)
            }
            def type = ClassUtils.forName(s, InjectTransform.classLoader).orElse(null)
            if (type != null) {
                return Optional.of(ClassHelper.make(type))
            }
            return Optional.empty()
        } as Function<String, Optional<ClassNode>>)

        if (adaptedType.isPresent()) {
            ClassNode typeToImplement = adaptedType.get()
            boolean isInterface = typeToImplement.isInterface()
            if (isInterface) {

                String packageName = concreteClass.packageName
                String declaringClassSimpleName = concreteClass.nameWithoutPackage
                String beanClassName = generateAdaptedMethodClassName(declaringClassSimpleName, typeToImplement, method)

                AopProxyWriter aopProxyWriter = new AopProxyWriter(
                        packageName,
                        beanClassName,
                        true,
                        false,
                        originatingElement,
                        methodAnnotationMetadata,
                        [typeToImplement.name] as Object[],
                        configurationMetadataBuilder,
                        ArrayUtils.EMPTY_OBJECT_ARRAY
                )

                aopProxyWriter.visitDefaultConstructor(methodAnnotationMetadata)

                beanDefinitionWriters.put(ClassHelper.make(packageName + '.' + beanClassName), aopProxyWriter)


                GenericsType[] typeArguments = typeToImplement.getGenericsTypes()
                Map<String, ClassNode> typeVariables = new HashMap<>(typeArguments?.size() ?: 1)

                for (GenericsType typeArgument : typeArguments) {
                    typeVariables.put(typeArgument.name, typeArgument.type)
                }

                InjectVisitor thisVisitor = this
                SourceUnit source = this.sourceUnit
                CompilationUnit unit = this.compilationUnit
                GroovyMethodElement sourceMethod = new GroovyMethodElement(
                        concreteClassElement,
                        source,
                        unit,
                        method,
                        methodAnnotationMetadata
                )
                PublicAbstractMethodVisitor visitor = new PublicAbstractMethodVisitor(source, unit) {
                    boolean first = true

                    @Override
                    void accept(ClassNode classNode, MethodNode targetMethod) {
                        if (!first) {
                            thisVisitor.addError("Interface to adapt [" + typeToImplement + "] is not a SAM type. More than one abstract method declared.", (MethodNode)method)
                            return
                        }
                        first = false
                        Parameter[] targetParameters = targetMethod.getParameters()
                        Parameter[] sourceParameters = method.getParameters()
                        Map<String, ClassNode> boundTypes = AstGenericUtils.createGenericsSpec(classNode)
                        if (targetParameters.size() == sourceParameters.size()) {

                            int i = 0
                            Map<String, Object> genericTypes = new HashMap<>()
                            for (Parameter targetElement in targetParameters) {

                                Parameter sourceElement = sourceParameters[i]

                                ClassNode targetType = targetElement.getType()
                                ClassNode sourceType = sourceElement.getType()

                                if (targetType.isGenericsPlaceHolder()) {
                                    GenericsType[] targetGenerics = targetType.genericsTypes

                                    if (targetGenerics) {
                                        String variableName = targetGenerics[0].name
                                        if (typeVariables.containsKey(variableName)) {
                                            targetType = typeVariables.get(variableName)

                                            genericTypes.put(variableName, AstGenericUtils.resolveTypeReference(sourceType, boundTypes))
                                        }
                                    }
                                }

                                if (!AstClassUtils.isSubclassOfOrImplementsInterface(sourceType, targetType)) {
                                    thisVisitor.addError("Cannot adapt method [${method.declaringClass.name}.$method.name(..)] to target method [${targetMethod.declaringClass.name}.$targetMethod.name(..)]. Argument type [" + sourceType.name + "] is not a subtype of type [$targetType.name] at position $i.", (MethodNode)method)
                                    return
                                }

                                i++
                            }

                            if (!genericTypes.isEmpty()) {
                                Map<String, Map<String, Object>> typeData = Collections.<String, Map<String, Object>>singletonMap(
                                        typeToImplement.name,
                                        genericTypes
                                )
                                aopProxyWriter.visitTypeArguments(
                                        finalizeTypeArguments(typeData)
                                )
                            }

                            Map<String, ParameterElement> targetMethodParamsToType = [:]
                            Map<String, ClassElement> targetMethodGenericParams = [:]
                            Map<String, AnnotationMetadata> targetAnnotationMetadata = [:]

                            String qualifier = concreteClassAnnotationMetadata.getValue(Named.class, String.class).orElse(null)


                            GroovyMethodElement groovyMethodElement = new GroovyMethodElement(
                                    concreteClassElement,
                                    source,
                                    unit,
                                    targetMethod,
                                    methodAnnotationMetadata
                            )

                            populateParameterData(
                                    groovyMethodElement,
                                    targetMethodParamsToType,
                                    targetMethodGenericParams,
                                    targetAnnotationMetadata,
                                    false
                            )

                            AnnotationClassValue[] adaptedArgumentTypes = new AnnotationClassValue[sourceParameters.length]
                            int j = 0
                            for (ParameterElement ve in sourceMethod.parameters) {
                                adaptedArgumentTypes[j] = new AnnotationClassValue(ve.type.name)
                                j++
                            }
                            groovyMethodElement.annotate(Adapter.class, { builder ->
                                builder.member(Adapter.InternalAttributes.ADAPTED_BEAN, new AnnotationClassValue<>(concreteClass.name))
                                builder.member(Adapter.InternalAttributes.ADAPTED_METHOD, method.name)
                                builder.member(Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES, adaptedArgumentTypes)
                                if (StringUtils.isNotEmpty(qualifier)) {
                                    builder.member(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier)
                                }
                            })

                            GroovyClassElement declaringElement = new GroovyClassElement(
                                    source,
                                    unit,
                                    targetMethod.declaringClass,
                                    AnnotationMetadata.EMPTY_METADATA
                            )
                            aopProxyWriter.visitAroundMethod(
                                    declaringElement,
                                    groovyMethodElement
                            )


                        } else {
                            thisVisitor.addError(
                                    "Cannot adapt method [${method.declaringClass.name}.$method.name(..)] to target method [${targetMethod.declaringClass.name}.$targetMethod.name(..)]. Argument lengths don't match.",
                                    (MethodNode) method
                            )
                        }
                    }
                }

                visitor.accept(typeToImplement)
            }

        }
    }

    private String generateAdaptedMethodClassName(String declaringClassSimpleName, ClassNode typeToImplement, MethodNode method) {
        String rootName = declaringClassSimpleName + '$' + typeToImplement.nameWithoutPackage + '$' + method.getName()
        return rootName + adaptedMethodIndex.incrementAndGet()
    }

    private void visitTypeArguments(ClassNode typeElement, BeanDefinitionWriter beanDefinitionWriter) {
        Map<String, Map<String, Object>> typeArguments = AstGenericUtils.buildAllGenericTypeInfo(typeElement)
        LinkedHashMap<String, Map<String, Object>> finalTypeArguments = finalizeTypeArguments(typeArguments)
        beanDefinitionWriter.visitTypeArguments(
                finalTypeArguments
        )
    }

    private static LinkedHashMap<String, Map<String, Object>> finalizeTypeArguments(Map<String, Map<String, Object>> typeArguments) {
        Map<String, Map<String, Object>> finalTypeArguments = new LinkedHashMap<>(typeArguments.size())
        for (entry in typeArguments) {
            Map<String, Object> newMap = new LinkedHashMap<>(entry.value.size())
            for (entry2 in entry.value) {
                def v = entry2.value
                if (v instanceof ClassNode) {
                    newMap.put(entry2.key, v.getName())
                } else {
                    newMap.put(entry2.key, v.toString())
                }
            }
            finalTypeArguments.put(entry.key, newMap)
        }
        finalTypeArguments
    }

    private void populateParameterData(
            GroovyMethodElement methodElement,
            Map<String, ParameterElement> paramsToType,
            Map<String, ClassElement> genericParams,
            Map<String, AnnotationMetadata> argumentAnnotationMetadata,
            boolean isConstructBinding) {
        for (ParameterElement param in methodElement.parameters) {
            String parameterName = param.name
            def annotationMetadata = param.annotationMetadata
            paramsToType.put(parameterName, param)
            genericParams.put(parameterName, param.genericType)



            if (isConstructBinding) {
                if (!annotationMetadata.hasStereotype(io.micronaut.context.annotation.Parameter, Property, Value)) {
                    if (!param.type.hasStereotype(Scope)) {
                        def propertyMetadata = configurationMetadataBuilder.visitProperty(
                                param.type.name,
                                parameterName,
                                null,
                                null
                        )
                        annotationMetadata = addPropertyMetadata(param, propertyMetadata)
                    }
                }
            }
            argumentAnnotationMetadata.put(parameterName, annotationMetadata)
        }
    }

    private void visitConfigurationBuilder(ClassElement declaringClass,
                                           AnnotationMetadata annotationMetadata,
                                           ClassElement classNode,
                                           BeanDefinitionVisitor writer) {
        Boolean allowZeroArgs = annotationMetadata.getValue(ConfigurationBuilder.class, "allowZeroArgs", Boolean.class).orElse(false)
        List<String> prefixes = Arrays.asList(annotationMetadata.getValue(ConfigurationBuilder.class, "prefixes", String[].class).orElse(["set"] as String[]))
        String configurationPrefix = annotationMetadata.getValue(ConfigurationBuilder.class, String.class)
                .map({ value -> value + "."}).orElse("")
        Set<String> includes = annotationMetadata.getValue(ConfigurationBuilder.class, "includes", Set.class).orElse(Collections.emptySet())
        Set<String> excludes = annotationMetadata.getValue(ConfigurationBuilder.class, "excludes", Set.class).orElse(Collections.emptySet())

        SourceUnit source = this.sourceUnit
        CompilationUnit compilationUnit = this.compilationUnit
        GroovyClassElement concreteClassElement = this.concreteClassElement
        PublicMethodVisitor visitor = new PublicMethodVisitor(source) {
            @Override
            void accept(ClassNode cn, MethodNode method) {
                String name = method.getName()
                String prefix = getMethodPrefix(name)
                String propertyName = NameUtils.decapitalize(name.substring(prefix.length()))
                if (shouldExclude(includes, excludes, propertyName)) {
                    return
                }
                GroovyMethodElement groovyMethodElement = new GroovyMethodElement(
                        concreteClassElement,
                        source,
                        compilationUnit,
                        method,
                        AstAnnotationUtils.getAnnotationMetadata(source, compilationUnit, method)
                )
                ParameterElement[] params = groovyMethodElement.parameters
                int paramCount = params.size()
                if (paramCount < 2) {
                    ParameterElement paramType = params.size() == 1 ? params[0] : null

                    PropertyMetadata metadata = configurationMetadataBuilder.visitProperty(
                            concreteClassElement.nativeType as ClassNode,
                            declaringClass.nativeType as ClassNode,
                            paramType?.type?.name,
                            configurationPrefix + propertyName,
                            null,
                            null
                    )

                    writer.visitConfigBuilderMethod(
                            prefix,
                            groovyMethodElement.returnType,
                            name,
                            paramType?.type,
                            paramType?.type?.typeArguments,
                            metadata.path
                    )

                } else if (paramCount == 2) {
                    // check the params are a long and a TimeUnit
                    ParameterElement first = params[0]
                    ParameterElement second = params[1]

                    PropertyMetadata metadata = configurationMetadataBuilder.visitProperty(
                            concreteClassElement.nativeType as ClassNode,
                            declaringClass.nativeType as ClassNode,
                            Duration.class.name,
                            configurationPrefix + propertyName,
                            null,
                            null
                    )

                    if (second.type.name == TimeUnit.class.name && first.type.name == "long") {
                        writer.visitConfigBuilderDurationMethod(
                                prefix,
                                groovyMethodElement.returnType,
                                name,
                                metadata.path
                        )
                    }
                }
            }

            @Override
            protected boolean isAcceptable(MethodNode node) {
                // ignore deprecated methods
                if (AstAnnotationUtils.hasStereotype(source, compilationUnit, node, Deprecated.class)) {
                    return false
                }
                int paramCount = node.getParameters().size()
                ((paramCount > 0 && paramCount < 3) || (allowZeroArgs && paramCount == 0)) &&
                        super.isAcceptable(node) &&
                        isPrefixedWith(node.getName())
            }

            private boolean isPrefixedWith(String name) {
                for (String prefix : prefixes) {
                    if (name.startsWith(prefix)) return true
                }
                return false
            }

            private String getMethodPrefix(String methodName) {
                for (String prefix : prefixes) {
                    if (methodName.startsWith(prefix)) {
                        return prefix
                    }
                }
                return methodName
            }
        }

        visitor.accept(classNode.nativeType as ClassNode)
    }

    private boolean shouldExclude(Set<String> includes, Set<String> excludes, String propertyName) {
        if (!includes.isEmpty() && !includes.contains(propertyName)) {
            return true
        }
        if (!excludes.isEmpty() && excludes.contains(propertyName)) {
            return true
        }
        return false
    }

    private boolean shouldExclude(ConfigurationMetadata configurationMetadata, String propertyName) {
        return shouldExclude(configurationMetadata.getIncludes(), configurationMetadata.getExcludes(), propertyName)
    }
}