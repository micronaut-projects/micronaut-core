/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.ast.groovy

import io.micronaut.context.annotation.Property
import io.micronaut.inject.annotation.DefaultAnnotationMetadata
import io.micronaut.inject.configuration.ConfigurationMetadata
import io.micronaut.inject.configuration.PropertyMetadata
import io.micronaut.inject.writer.DirectoryClassWriterOutputVisitor

import static org.codehaus.groovy.ast.ClassHelper.makeCached
import static org.codehaus.groovy.ast.tools.GeneralUtils.getGetterName
import static org.codehaus.groovy.ast.tools.GeneralUtils.getSetterName

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import io.micronaut.aop.Around
import io.micronaut.aop.Interceptor
import io.micronaut.aop.Introduction
import io.micronaut.aop.writer.AopProxyWriter
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder
import io.micronaut.ast.groovy.config.GroovyConfigurationMetadataBuilder
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.ast.groovy.utils.AstGenericUtils
import io.micronaut.ast.groovy.utils.AstMessageUtils
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader
import io.micronaut.ast.groovy.utils.PublicAbstractMethodVisitor
import io.micronaut.ast.groovy.utils.PublicMethodVisitor
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.ast.groovy.visitor.LoadedVisitor
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Configuration
import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Internal
import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.util.ArrayUtils
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AnnotationMetadataReference
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder
import io.micronaut.inject.processing.ProcessedTypes
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanConfigurationWriter
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.inject.writer.ClassWriterOutputVisitor
import io.micronaut.inject.writer.ExecutableMethodWriter
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PackageNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.io.StringReaderSource
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Scope
import java.lang.reflect.Modifier

/**
 * An AST transformation that produces metadata for use by the injection container
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class InjectTransform implements ASTTransformation, CompilationUnitAware {

    CompilationUnit unit
    ConfigurationMetadataBuilder<ClassNode> configurationMetadataBuilder = new GroovyConfigurationMetadataBuilder()

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        Map<AnnotatedNode, BeanDefinitionVisitor> beanDefinitionWriters = [:]
        File classesDir = source.configuration.targetDirectory
        DirectoryClassWriterOutputVisitor outputVisitor = new DirectoryClassWriterOutputVisitor(
                classesDir
        )
        List<ClassNode> classes = moduleNode.getClasses()
        if (classes.size() == 1) {
            ClassNode classNode = classes[0]
            if (classNode.nameWithoutPackage == 'package-info') {
                PackageNode packageNode = classNode.getPackage()
                if (AstAnnotationUtils.hasStereotype(packageNode, Configuration)) {
                    BeanConfigurationWriter writer = new BeanConfigurationWriter(classNode.packageName, AstAnnotationUtils.getAnnotationMetadata(packageNode))
                    try {
                        writer.accept(outputVisitor)
                        outputVisitor.finish()
                    } catch (Throwable e) {
                        AstMessageUtils.error(source, classNode, "Error generating bean configuration for package-info class [${classNode.name}]: $e.message")
                    }
                }

                return
            }
        }

        for (ClassNode classNode in classes) {
            if ((classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers()))) {
                continue
            } else {
                if (classNode.isInterface()) {
                    if (AstAnnotationUtils.hasStereotype(classNode, InjectVisitor.INTRODUCTION_TYPE)) {
                        InjectVisitor injectVisitor = new InjectVisitor(source, classNode, configurationMetadataBuilder)
                        injectVisitor.visitClass(classNode)
                        beanDefinitionWriters.putAll(injectVisitor.beanDefinitionWriters)
                    }
                } else {
                    InjectVisitor injectVisitor = new InjectVisitor(source, classNode, configurationMetadataBuilder)
                    injectVisitor.visitClass(classNode)
                    beanDefinitionWriters.putAll(injectVisitor.beanDefinitionWriters)
                }
            }
        }

        boolean defineClassesInMemory = source.classLoader instanceof InMemoryByteCodeGroovyClassLoader
        Map<String, ByteArrayOutputStream> classStreams = null

        for (entry in beanDefinitionWriters) {
            BeanDefinitionVisitor beanDefWriter = entry.value
            String beanTypeName = beanDefWriter.beanTypeName
            AnnotatedNode beanClassNode = entry.key
            try {
                String beanDefinitionName = beanDefWriter.beanDefinitionName
                BeanDefinitionReferenceWriter beanReferenceWriter = new BeanDefinitionReferenceWriter(beanTypeName, beanDefinitionName, beanDefWriter.annotationMetadata)

                beanReferenceWriter.setRequiresMethodProcessing(beanDefWriter.requiresMethodProcessing());
                beanReferenceWriter.setContextScope(AstAnnotationUtils.hasStereotype(beanClassNode, Context))

                Optional<String> replacesOpt = AstAnnotationUtils
                    .getAnnotationMetadata(beanClassNode)
                    .getValue(Replaces, String.class)

                if (replacesOpt.isPresent()) {
                    beanReferenceWriter.setReplaceBeanName(replacesOpt.get())
                }
                beanDefWriter.visitBeanDefinitionEnd()
                if (classesDir != null) {
                    beanReferenceWriter.accept(outputVisitor)
                    beanDefWriter.accept(outputVisitor)
                } else if (source.source instanceof StringReaderSource && defineClassesInMemory) {
                    if (classStreams == null) {
                        classStreams = [:]
                    }
                    ClassWriterOutputVisitor visitor = new ClassWriterOutputVisitor() {
                        @Override
                        OutputStream visitClass(String classname) throws IOException {
                            ByteArrayOutputStream stream = new ByteArrayOutputStream()
                            classStreams.put(classname, stream)
                            return stream
                        }

                        @Override
                        void visitServiceDescriptor(String type, String classname) {
                            // no-op
                        }

                        @Override
                        Optional<File> visitMetaInfFile(String path) throws IOException {
                            return Optional.empty()
                        }

                        @Override
                        void finish() {
                            // no-op
                        }
                    }
                    beanReferenceWriter.accept(visitor)
                    beanDefWriter.accept(visitor)

                }



            } catch (Throwable e) {
                AstMessageUtils.error(source, beanClassNode, "Error generating bean definition class for dependency injection of class [${beanTypeName}]: $e.message")
                if (e.message == null) {
                    e.printStackTrace(System.err)
                }
            }
        }
        if(!beanDefinitionWriters.isEmpty()) {

            try {
                outputVisitor.finish()

            } catch (Throwable e) {
                AstMessageUtils.error(source, moduleNode, "Error generating META-INF/services files: $e.message")
                if (e.message == null) {
                    e.printStackTrace(System.err)
                }
            }
        }

        if (classStreams != null) {
            // for testing try to load them into current classloader
            InMemoryByteCodeGroovyClassLoader classLoader = (InMemoryByteCodeGroovyClassLoader) source.classLoader

            if (defineClassesInMemory) {

                if (classLoader != null) {
                    for (streamEntry in classStreams) {
                        classLoader.addClass(streamEntry.key, streamEntry.value.toByteArray())
                    }
                }
            }
        }

        AstAnnotationUtils.invalidateCache()
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }

    private static class InjectVisitor extends ClassCodeVisitorSupport {
        public static final String AROUND_TYPE = "io.micronaut.aop.Around"
        public static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction"
        final SourceUnit sourceUnit
        final ClassNode concreteClass
        final AnnotationMetadata annotationMetadata
        final boolean isConfigurationProperties
        final boolean isFactoryClass
        final boolean isExecutableType
        final boolean isAopProxyType
        final OptionalValues<Boolean> aopSettings
        final ConfigurationMetadataBuilder<ClassNode> configurationMetadataBuilder
        ConfigurationMetadata configurationMetadata

        final Map<AnnotatedNode, BeanDefinitionVisitor> beanDefinitionWriters = [:]
        private BeanDefinitionVisitor beanWriter
        BeanDefinitionVisitor aopProxyWriter

        InjectVisitor(SourceUnit sourceUnit, ClassNode targetClassNode, ConfigurationMetadataBuilder<ClassNode> configurationMetadataBuilder) {
            this(sourceUnit, targetClassNode, null, configurationMetadataBuilder)
        }

        InjectVisitor(SourceUnit sourceUnit, ClassNode targetClassNode, Boolean configurationProperties, ConfigurationMetadataBuilder<ClassNode> configurationMetadataBuilder) {
            this.sourceUnit = sourceUnit
            this.configurationMetadataBuilder = configurationMetadataBuilder
            this.concreteClass = targetClassNode
            def annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(targetClassNode)
            this.annotationMetadata = annotationMetadata
            this.isFactoryClass = annotationMetadata.hasStereotype(Factory)
            this.isAopProxyType = annotationMetadata.hasStereotype(AROUND_TYPE) && !targetClassNode.isAbstract()
            this.aopSettings = isAopProxyType ? annotationMetadata.getValues(AROUND_TYPE, Boolean.class) : OptionalValues.<Boolean> empty()
            this.isExecutableType = isAopProxyType || annotationMetadata.hasStereotype(Executable)
            this.isConfigurationProperties = configurationProperties != null ? configurationProperties : annotationMetadata.hasDeclaredStereotype(ConfigurationReader)
            if(isConfigurationProperties) {
                this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                        concreteClass,
                        null
                )
            }
            if (isFactoryClass || isConfigurationProperties || annotationMetadata.hasStereotype(Bean, Scope)) {
                defineBeanDefinition(concreteClass)
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
            AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(node)
            if (annotationMetadata.hasStereotype(INTRODUCTION_TYPE)) {
                String packageName = node.packageName
                String beanClassName = node.nameWithoutPackage

                Object[] aroundInterceptors = annotationMetadata
                    .getAnnotationNamesByStereotype(AROUND_TYPE)
                    .toArray()
                Object[] introductionInterceptors = annotationMetadata
                    .getAnnotationNamesByStereotype(Introduction.class)
                    .toArray()

                Object[] interceptorTypes = ArrayUtils.concat(aroundInterceptors, introductionInterceptors)
                String[] interfaceTypes = annotationMetadata.getValue(Introduction.class, "interfaces", String[].class).orElse(new String[0])

                boolean isInterface = node.isInterface()
                AopProxyWriter aopProxyWriter = new AopProxyWriter(
                    packageName,
                    beanClassName,
                    isInterface,
                    annotationMetadata,
                    interfaceTypes,
                    interceptorTypes)
                populateProxyWriterConstructor(node, aopProxyWriter)
                beanDefinitionWriters.put(node, aopProxyWriter)
                visitIntroductionTypePublicMethods(aopProxyWriter, node)
                if (ArrayUtils.isNotEmpty(interfaceTypes)) {
                    List<AnnotationNode> annotationNodes = node.annotations
                    Set<ClassNode> interfacesToVisit = []

                    populateIntroducedInterfaces(annotationNodes, interfacesToVisit)

                    if (!interfacesToVisit.isEmpty()) {
                        for (itce in interfacesToVisit) {
                            visitIntroductionTypePublicMethods(aopProxyWriter, itce)
                        }
                    }
                }
            } else {
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
                } else if (AstAnnotationUtils.hasStereotype(ann.classNode, Introduction)) {
                    populateIntroducedInterfaces(ann.classNode.annotations, interfacesToVisit)
                }
            }
        }

        protected void visitIntroductionTypePublicMethods(AopProxyWriter aopProxyWriter, ClassNode node) {
            AnnotationMetadata typeAnnotationMetadata = aopProxyWriter.getAnnotationMetadata()
            PublicMethodVisitor publicMethodVisitor = new PublicAbstractMethodVisitor(sourceUnit) {

                @Override
                void accept(ClassNode classNode, MethodNode methodNode) {
                    Map<String, Object> targetMethodParamsToType = [:]
                    Map<String, AnnotationMetadata> targetAnnotationMetadata = [:]
                    Map<String, Map<String, Object>> targetMethodGenericTypeMap = [:]

                    Map<String, ClassNode> boundTypes = AstGenericUtils.createGenericsSpec(classNode)

                    if (!classNode.isPrimaryClassNode()) {
                        AstGenericUtils.createGenericsSpec(methodNode, boundTypes)
                    }
                    Object resolvedReturnType = AstGenericUtils.resolveTypeReference(methodNode.returnType, boundTypes)
                    Map<String, Object> resolvedGenericTypes = AstGenericUtils.buildGenericTypeInfo(
                        methodNode.returnType,
                        boundTypes
                    )
                    populateParameterData(
                        methodNode.parameters,
                        targetMethodParamsToType,
                        targetAnnotationMetadata,
                        targetMethodGenericTypeMap)


                    AnnotationMetadata annotationMetadata
                    if (AstAnnotationUtils.isAnnotated(methodNode) || AstAnnotationUtils.hasAnnotation(methodNode, Override)) {
                        annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(node, methodNode)
                    } else {
                        annotationMetadata = new AnnotationMetadataReference(
                            aopProxyWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                            typeAnnotationMetadata
                        )
                    }
                    aopProxyWriter.visitAroundMethod(
                        AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                        resolveReturnType(classNode, methodNode, boundTypes),
                        resolvedReturnType,
                        resolvedGenericTypes,
                        methodNode.name,
                        targetMethodParamsToType,
                        targetAnnotationMetadata,
                        targetMethodGenericTypeMap,
                        annotationMetadata
                    )
                }


            }
            publicMethodVisitor.accept(node)
        }

        private Object resolveReturnType(ClassNode classNode, MethodNode methodNode, Map<String, ClassNode> boundTypes) {
            boolean isPrimaryClassNode = classNode.isPrimaryClassNode()
            ClassNode returnType = methodNode.returnType
            if (isPrimaryClassNode || classNode.genericsTypes) {
                if (!isPrimaryClassNode && returnType.isArray()) {
                    Map<String, ClassNode> genericSpec = AstGenericUtils.createGenericsSpec(classNode.redirect())
                    return AstGenericUtils.resolveTypeReference(returnType, genericSpec)
                } else {

                    return AstGenericUtils.resolveTypeReference(returnType)
                }
            } else {
                return AstGenericUtils.resolveTypeReference(returnType, boundTypes)
            }
        }

        @Override
        protected void visitConstructorOrMethod(MethodNode methodNode, boolean isConstructor) {
            String methodName = methodNode.name
            ClassNode declaringClass = methodNode.declaringClass
            AnnotationMetadata methodAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(methodNode)
            if (isFactoryClass && !isConstructor && methodAnnotationMetadata.hasDeclaredStereotype(Bean, Scope)) {
                methodAnnotationMetadata = new GroovyAnnotationMetadataBuilder().buildForMethod(methodNode)
                ClassNode producedType = methodNode.returnType
                String beanDefinitionPackage = concreteClass.packageName
                String upperCaseMethodName = NameUtils.capitalize(methodNode.getName())
                String factoryMethodBeanDefinitionName =
                    beanDefinitionPackage + '.$' + concreteClass.nameWithoutPackage + '$' + upperCaseMethodName + "Definition"

                BeanDefinitionWriter beanMethodWriter = new BeanDefinitionWriter(
                    producedType.packageName,
                    producedType.nameWithoutPackage,
                    factoryMethodBeanDefinitionName,
                    producedType.name,
                    producedType.isInterface(),
                    methodAnnotationMetadata
                )

                Map<String, Object> paramsToType = [:]
                Map<String, AnnotationMetadata> argumentAnnotationMetadata = [:]
                Map<String, Map<String, Object>> genericTypeMap = [:]
                populateParameterData(methodNode.parameters, paramsToType, argumentAnnotationMetadata, genericTypeMap)

                beanMethodWriter.visitBeanFactoryMethod(
                        AstGenericUtils.resolveTypeReference(concreteClass),
                        AstGenericUtils.resolveTypeReference(producedType),
                        methodName,
                        methodAnnotationMetadata,
                        paramsToType,
                        argumentAnnotationMetadata,
                        genericTypeMap
                )

                if (methodAnnotationMetadata.hasStereotype(AROUND_TYPE)) {
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
                        interceptorTypeReferences)
                    if (producedType.isInterface()) {
                        proxyWriter.visitBeanDefinitionConstructor(
                                AnnotationMetadata.EMPTY_METADATA,
                                false
                        )
                    } else {
                        populateProxyWriterConstructor(producedType, proxyWriter)
                    }

                    new PublicMethodVisitor(sourceUnit) {
                        @Override
                        void accept(ClassNode classNode, MethodNode targetBeanMethodNode) {
                            Map<String, Object> targetMethodParamsToType = [:]
                            Map<String, AnnotationMetadata> targetAnnotationMetadata = [:]
                            Map<String, Map<String, Object>> targetMethodGenericTypeMap = [:]
                            Map<String, ClassNode> boundTypes = AstGenericUtils.createGenericsSpec(classNode)
                            Object resolvedReturnType = AstGenericUtils.resolveTypeReference(targetBeanMethodNode.returnType, boundTypes)
                            Object returnTypeReference = resolveReturnType(classNode, targetBeanMethodNode, boundTypes)
                            Map<String, Object> resolvedGenericTypes = AstGenericUtils.buildGenericTypeInfo(
                                targetBeanMethodNode.returnType,
                                boundTypes
                            )

                            populateParameterData(
                                targetBeanMethodNode.parameters,
                                targetMethodParamsToType,
                                targetAnnotationMetadata,
                                targetMethodGenericTypeMap)
                            AnnotationMetadata annotationMetadata
                            if (AstAnnotationUtils.isAnnotated(methodNode)) {
                                annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(methodNode, targetBeanMethodNode);
                            } else {
                                annotationMetadata = new AnnotationMetadataReference(
                                    beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    methodAnnotationMetadata
                                )
                            }

                            ExecutableMethodWriter writer = beanMethodWriter.visitExecutableMethod(
                                AstGenericUtils.resolveTypeReference(targetBeanMethodNode.declaringClass),
                                returnTypeReference,
                                resolvedReturnType,
                                resolvedGenericTypes,
                                targetBeanMethodNode.name,
                                targetMethodParamsToType,
                                targetAnnotationMetadata,
                                targetMethodGenericTypeMap,
                                annotationMetadata
                            )

                            proxyWriter.visitAroundMethod(
                                AstGenericUtils.resolveTypeReference(targetBeanMethodNode.declaringClass),
                                returnTypeReference,
                                resolvedReturnType,
                                resolvedGenericTypes,
                                targetBeanMethodNode.name,
                                targetMethodParamsToType,
                                targetAnnotationMetadata,
                                targetMethodGenericTypeMap,
                                new AnnotationMetadataReference(writer.getClassName(), annotationMetadata)
                            )
                        }
                    }.accept(methodNode.getReturnType())
                    beanDefinitionWriters.put(new AnnotatedNode(), proxyWriter)

                }
                Optional<String> preDestroy = methodAnnotationMetadata.getValue(Bean, "preDestroy", String.class)
                if (preDestroy.isPresent()) {
                    String destroyMethodName = preDestroy.get()
                    MethodNode destroyMethod = producedType.getMethod(destroyMethodName)
                    if (destroyMethod != null) {
                        beanMethodWriter.visitPreDestroyMethod(destroyMethod.declaringClass.name, destroyMethodName)
                    }
                }
                beanDefinitionWriters.put(methodNode, beanMethodWriter)
            } else if (methodAnnotationMetadata.hasStereotype(Inject.name, ProcessedTypes.POST_CONSTRUCT, ProcessedTypes.PRE_DESTROY)) {
                defineBeanDefinition(concreteClass)

                if (!isConstructor) {
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
                        boolean overriddenInjected = overridden && AstAnnotationUtils.hasStereotype(overriddenMethod, Inject)

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

                        Map<String, Object> paramsToType = [:]
                        Map<String, AnnotationMetadata> argumentAnnotationMetadata = [:]
                        Map<String, Map<String, Object>> genericTypeMap = [:]
                        populateParameterData(methodNode.parameters, paramsToType, argumentAnnotationMetadata, genericTypeMap)

                        if (methodAnnotationMetadata.hasStereotype(ProcessedTypes.POST_CONSTRUCT)) {
                            getBeanWriter().visitPostConstructMethod(
                                    AstGenericUtils.resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodName,
                                    paramsToType,
                                    argumentAnnotationMetadata,
                                    genericTypeMap,
                                    methodAnnotationMetadata)
                        } else if (methodAnnotationMetadata.hasStereotype(ProcessedTypes.PRE_DESTROY)) {
                            getBeanWriter().visitPreDestroyMethod(
                                    AstGenericUtils.resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodName,
                                    paramsToType,
                                    argumentAnnotationMetadata,
                                    genericTypeMap,
                                    methodAnnotationMetadata)
                        } else {
                            getBeanWriter().visitMethodInjectionPoint(
                                    AstGenericUtils.resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodName,
                                    paramsToType,
                                    argumentAnnotationMetadata,
                                    genericTypeMap,
                                    methodAnnotationMetadata)
                        }
                    }
                }
            } else if (!isConstructor) {
                boolean hasInvalidModifiers = methodNode.isStatic() || methodNode.isAbstract() || methodNode.isSynthetic() || methodAnnotationMetadata.hasAnnotation(Internal) || methodNode.isPrivate()
                boolean isPublic = methodNode.isPublic() && !hasInvalidModifiers
                boolean isExecutable = ((isExecutableType && isPublic) || methodAnnotationMetadata.hasStereotype(Executable)) && !hasInvalidModifiers
                if (isExecutable) {
                    if (declaringClass != ClassHelper.OBJECT_TYPE) {

                        defineBeanDefinition(concreteClass)
                        Map<String, Object> returnTypeGenerics = AstGenericUtils.buildGenericTypeInfo(methodNode.returnType, GenericsUtils.createGenericsSpec(concreteClass))

                        Map<String, Object> paramsToType = [:]
                        Map<String, AnnotationMetadata> qualifierTypes = [:]
                        Map<String, Map<String, Object>> genericTypeMap = [:]
                        populateParameterData(methodNode.parameters, paramsToType, qualifierTypes, genericTypeMap)

                        boolean preprocess = methodAnnotationMetadata.getValue(Executable.class, "processOnStartup", Boolean.class).orElse(false);
                        if (preprocess) {
                            getBeanWriter().setRequiresMethodProcessing(true)
                        }
                        ExecutableMethodWriter executableMethodWriter = getBeanWriter().visitExecutableMethod(
                            AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                            AstGenericUtils.resolveTypeReference(methodNode.returnType),
                            AstGenericUtils.resolveTypeReference(methodNode.returnType),
                            returnTypeGenerics,
                            methodName,
                            paramsToType,
                            qualifierTypes,
                            genericTypeMap, methodAnnotationMetadata)

                        if ((isAopProxyType && isPublic) || (methodAnnotationMetadata.hasStereotype(AROUND_TYPE) && !concreteClass.isAbstract())) {

                            Object[] interceptorTypeReferences = methodAnnotationMetadata.getAnnotationNamesByStereotype(Around).toArray()
                            OptionalValues<Boolean> aopSettings = methodAnnotationMetadata.getValues(AROUND_TYPE, Boolean)
                            AopProxyWriter proxyWriter = resolveProxyWriter(
                                aopSettings,
                                false,
                                interceptorTypeReferences
                            )

                            if (proxyWriter != null && !methodNode.isFinal()) {

                                proxyWriter.visitInterceptorTypes(interceptorTypeReferences)
                                proxyWriter.visitAroundMethod(
                                    AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    returnTypeGenerics,
                                    methodName,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap,
                                    new AnnotationMetadataReference(executableMethodWriter.getClassName(), methodAnnotationMetadata)
                                )
                            }
                        }
                    }
                }
                if (isConfigurationProperties && isPublic && NameUtils.isSetterName(methodNode.name) && methodNode.parameters.length == 1) {
                    String propertyName = NameUtils.getPropertyNameForSetter(methodNode.name)
                    if (declaringClass.getField(propertyName) == null) {

                        Parameter parameter = methodNode.parameters[0]

                        PropertyMetadata propertyMetadata = configurationMetadataBuilder.visitProperty(
                                concreteClass,
                                declaringClass,
                                parameter.type.name,
                                propertyName,
                                null,
                                null
                        );

                        methodAnnotationMetadata = DefaultAnnotationMetadata.mutateMember(
                                methodAnnotationMetadata,
                                Property.name,
                                "name",
                                propertyMetadata.path
                        )

                        getBeanWriter().visitSetterValue(
                            AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                            methodAnnotationMetadata,
                            false,
                            resolveParameterType(parameter),
                            methodNode.name,
                            resolveGenericTypes(parameter),
                            AstAnnotationUtils.getAnnotationMetadata(parameter),
                            true
                        )
                    }
                }
            }
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
                    interceptorTypeReferences)

                ClassNode targetClass = concreteClass
                populateProxyWriterConstructor(targetClass, proxyWriter)
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

        protected void populateProxyWriterConstructor(ClassNode targetClass, AopProxyWriter proxyWriter) {
            List<ConstructorNode> constructors = targetClass.getDeclaredConstructors()
            if (constructors.isEmpty()) {
                proxyWriter.visitBeanDefinitionConstructor(
                        AnnotationMetadata.EMPTY_METADATA,
                        false
                )
            } else {
                ConstructorNode constructorNode = findConcreteConstructor(constructors)

                if (constructorNode != null) {
                    Map<String, Object> constructorParamsToType = [:]
                    Map<String, AnnotationMetadata> constructorArgumentMetadata = [:]
                    Map<String, Map<String, Object>> constructorGenericTypeMap = [:]
                    Parameter[] parameters = constructorNode.parameters
                    populateParameterData(parameters,
                                          constructorParamsToType,
                                          constructorArgumentMetadata,
                                          constructorGenericTypeMap)
                    proxyWriter.visitBeanDefinitionConstructor(
                            AstAnnotationUtils.getAnnotationMetadata(constructorNode),
                            constructorNode.isPrivate(),
                            constructorParamsToType,
                            constructorArgumentMetadata,
                            constructorGenericTypeMap
                    )
                } else {
                    addError("Class must have at least one non private constructor in order to be a candidate for dependency injection", targetClass)
                }

            }
        }

        protected boolean isPackagePrivate(AnnotatedNode annotatedNode, int modifiers) {
            return ((!Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers) && !Modifier.isPrivate(modifiers)) || !annotatedNode.getAnnotations(makeCached(PackageScope)).isEmpty())
        }

        @Override
        void visitField(FieldNode fieldNode) {
            if (fieldNode.name == 'metaClass') return
            int modifiers = fieldNode.modifiers
            if (Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)) {
                return
            }
            if (fieldNode.isSynthetic() && !isPackagePrivate(fieldNode, fieldNode.modifiers)) {
                return
            }
            ClassNode declaringClass = fieldNode.declaringClass
            AnnotationMetadata fieldAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(fieldNode)
            boolean isInject = fieldAnnotationMetadata.hasStereotype(Inject)
            boolean isValue = !isInject && (fieldAnnotationMetadata.hasStereotype(Value) || isConfigurationProperties)

            if ((isInject || isValue) && declaringClass.getProperty(fieldNode.getName()) == null) {
                defineBeanDefinition(concreteClass)
                if (!fieldNode.isStatic()) {

                    boolean isPrivate = Modifier.isPrivate(modifiers)
                    boolean requiresReflection = isPrivate || isInheritedAndNotPublic(fieldNode, fieldNode.declaringClass, modifiers)
                    if (!getBeanWriter().isValidated()) {
                        if (fieldAnnotationMetadata.hasStereotype("javax.validation.Constraint")) {
                            getBeanWriter().setValidated(true)
                        }
                    }
                    String fieldName = fieldNode.name
                    Object fieldType = AstGenericUtils.resolveTypeReference(fieldNode.type)
                    if (isValue) {

                        if (isConfigurationProperties && fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                            getBeanWriter().visitConfigBuilderField(fieldType, fieldName, fieldAnnotationMetadata, configurationMetadataBuilder)
                            try {
                                visitConfigurationBuilder(fieldAnnotationMetadata, fieldNode.type, getBeanWriter())
                            } finally {
                                getBeanWriter().visitConfigBuilderEnd()
                            }
                        } else {
                            if(isConfigurationProperties) {
                                PropertyMetadata propertyMetadata = configurationMetadataBuilder.visitProperty(
                                        concreteClass,
                                        declaringClass,
                                        fieldNode.type.name,
                                        fieldName,
                                        null, // TODO: fix groovy doc support
                                        null
                                )
                                fieldAnnotationMetadata = DefaultAnnotationMetadata.mutateMember(
                                        fieldAnnotationMetadata,
                                        Property.name,
                                        "name",
                                        propertyMetadata.path
                                )
                            }
                            getBeanWriter().visitFieldValue(
                                AstGenericUtils.resolveTypeReference(declaringClass),
                                fieldType,
                                fieldName,
                                requiresReflection,
                                fieldAnnotationMetadata,
                                AstGenericUtils.buildGenericTypeInfo(fieldNode.type, Collections.emptyMap()),
                                isConfigurationProperties
                            )
                        }
                    } else {
                        getBeanWriter().visitFieldInjectionPoint(
                                AstGenericUtils.resolveTypeReference(declaringClass),
                                fieldType,
                                fieldName,
                                requiresReflection,
                                fieldAnnotationMetadata,
                                AstGenericUtils.buildGenericTypeInfo(fieldNode.type, Collections.emptyMap())
                        )
                    }
                }
            }
        }


        Object resolveParameterType(Parameter parameter) {
            ClassNode parameterType = parameter.type
            if (parameterType.isResolved()) {
                parameterType.typeClass
            } else {
                parameterType.name
            }
        }

        Map<String, Object> resolveGenericTypes(Parameter parameter) {
            ClassNode parameterType = parameter.type
            GenericsType[] genericsTypes = parameterType.genericsTypes
            if (genericsTypes != null && genericsTypes.length > 0) {
                AstGenericUtils.extractPlaceholders(parameterType)
            } else if (parameterType.isArray()) {
                Map<String, Object> genericTypeList = [:]
                genericTypeList.put('E', AstGenericUtils.resolveTypeReference(parameterType.componentType))
                genericTypeList
            }
        }

        @Override
        void visitProperty(PropertyNode propertyNode) {
            FieldNode fieldNode = propertyNode.field
            if (fieldNode.name == 'metaClass') return
            def modifiers = propertyNode.getModifiers()
            if (Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)) {
                return
            }
            AnnotationMetadata fieldAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(fieldNode)
            boolean isInject = fieldNode != null && fieldAnnotationMetadata.hasStereotype(Inject)
            boolean isValue = !isInject && fieldNode != null && (fieldAnnotationMetadata.hasStereotype(Value) || isConfigurationProperties)
            String propertyName = propertyNode.name
            if (!propertyNode.isStatic() && (isInject || isValue)) {
                defineBeanDefinition(concreteClass)
                ClassNode fieldType = fieldNode.type

                GenericsType[] genericsTypes = fieldType.genericsTypes
                Map<String, Object> genericTypeList = null
                if (genericsTypes != null && genericsTypes.length > 0) {
                    genericTypeList = AstGenericUtils.buildGenericTypeInfo(fieldType, GenericsUtils.createGenericsSpec(concreteClass))
                } else if (fieldType.isArray()) {
                    genericTypeList = [:]
                    genericTypeList.put(fieldNode.name, AstGenericUtils.resolveTypeReference(fieldType.componentType))
                }
                ClassNode declaringClass = fieldNode.declaringClass
                if (!getBeanWriter().isValidated()) {
                    if (fieldAnnotationMetadata.hasStereotype("javax.validation.Constraint")) {
                        getBeanWriter().setValidated(true)
                    }
                }

                Object fieldTypeReference = AstGenericUtils.resolveTypeReference(fieldType)
                if (isInject) {
                    getBeanWriter().visitMethodInjectionPoint(
                        AstGenericUtils.resolveTypeReference(declaringClass),
                        false,
                        void.class,
                        getSetterName(propertyName),
                        Collections.singletonMap(propertyName, fieldTypeReference),
                        Collections.singletonMap(propertyName, fieldAnnotationMetadata),
                        Collections.singletonMap(propertyName, genericTypeList),
                        fieldAnnotationMetadata
                    )
                } else if (isValue) {
                    if (isConfigurationProperties && fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                        Object resolvedFieldType = fieldNode.type.isResolved() ? fieldNode.type.typeClass : fieldNode.type.name
                        getBeanWriter().visitConfigBuilderMethod(
                            resolvedFieldType,
                            getGetterName(propertyNode),
                            fieldAnnotationMetadata,
                            configurationMetadataBuilder)
                        try {
                            visitConfigurationBuilder(fieldAnnotationMetadata, fieldNode.type, getBeanWriter())
                        } finally {
                            getBeanWriter().visitConfigBuilderEnd()
                        }
                    } else {
                        if(isConfigurationProperties) {
                            PropertyMetadata propertyMetadata = configurationMetadataBuilder.visitProperty(
                                    concreteClass,
                                    declaringClass,
                                    propertyNode.type.name,
                                    propertyNode.name,
                                    null, // TODO: fix groovy doc support
                                    null
                            )
                            fieldAnnotationMetadata = DefaultAnnotationMetadata.mutateMember(
                                    fieldAnnotationMetadata,
                                    Property.name,
                                    "name",
                                    propertyMetadata.path
                            )
                        }
                        getBeanWriter().visitSetterValue(
                            AstGenericUtils.resolveTypeReference(declaringClass),
                            fieldAnnotationMetadata,
                            false,
                                fieldTypeReference,
                            fieldNode.name,
                            getSetterName(propertyName),
                            genericTypeList,
                            isConfigurationProperties
                        )
                    }
                }
            } else if (isAopProxyType && !propertyNode.isStatic()) {
                AopProxyWriter aopWriter = (AopProxyWriter) aopProxyWriter
                if (aopProxyWriter != null) {
                    Map<String, Map<String, Object>> resolvedGenericTypes =
                            [(propertyName): AstGenericUtils.extractPlaceholders(propertyNode.type)]
                    Map<String, Object> resolvedArguments =
                            [(propertyName): AstGenericUtils.resolveTypeReference(propertyNode.type)]

                    AnnotationMetadata fieldMetadata = AstAnnotationUtils.getAnnotationMetadata(propertyNode.field)

                    Map<String, AnnotationMetadata> resolvedAnnotationMetadata
                    if (fieldMetadata != null) {
                        resolvedAnnotationMetadata = [(propertyName): fieldMetadata]
                    } else {
                        resolvedAnnotationMetadata = Collections.emptyMap()
                    }
                    aopWriter.visitAroundMethod(
                        propertyNode.getDeclaringClass().name,
                        void.class,
                        void.class,
                        Collections.emptyMap(),
                        getSetterName(propertyName),
                        resolvedArguments,
                        resolvedAnnotationMetadata,
                        resolvedGenericTypes,
                        fieldAnnotationMetadata
                    )
                }
            }
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
                ClassNode providerGenericType = AstGenericUtils.resolveInterfaceGenericType(classNode, Provider)
                boolean isProvider = providerGenericType != null
                AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(classNode)
                if(configurationMetadata != null) {
                    annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                            annotationMetadata,
                            ConfigurationReader.class.getName(),
                            "prefix",
                            configurationMetadata.getName()
                    )
                }
                if (isProvider) {
                    beanWriter = new BeanDefinitionWriter(
                        classNode.packageName,
                        classNode.nameWithoutPackage,
                        providerGenericType.name,
                        classNode.isInterface(),
                        annotationMetadata)
                } else {

                    beanWriter = new BeanDefinitionWriter(
                        classNode.packageName,
                        classNode.nameWithoutPackage,
                        annotationMetadata)
                }
                beanDefinitionWriters.put(classNode, beanWriter)

                List<ConstructorNode> constructors = classNode.getDeclaredConstructors()

                if (constructors.isEmpty()) {
                    beanWriter.visitBeanDefinitionConstructor(
                            AnnotationMetadata.EMPTY_METADATA,
                            false,
                            Collections.emptyMap(),
                            null,
                            null
                    )

                } else {
                    ConstructorNode constructorNode = findConcreteConstructor(constructors)
                    if (constructorNode != null) {
                        Map<String, Object> paramsToType = [:]
                        Map<String, AnnotationMetadata> qualifierTypes = [:]
                        Map<String, Map<String, Object>> genericTypeMap = [:]
                        Parameter[] parameters = constructorNode.parameters
                        populateParameterData(parameters, paramsToType, qualifierTypes, genericTypeMap)
                        beanWriter.visitBeanDefinitionConstructor(
                                AstAnnotationUtils.getAnnotationMetadata(constructorNode),
                                constructorNode.isPrivate(),
                                paramsToType,
                                qualifierTypes,
                                genericTypeMap
                        )
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

        private ConstructorNode findConcreteConstructor(List<ConstructorNode> constructors) {
            List<ConstructorNode> nonPrivateConstructors = findNonPrivateConstructors(constructors)

            ConstructorNode constructorNode
            if (nonPrivateConstructors.size() == 1) {
                constructorNode = nonPrivateConstructors[0]
            } else {
                constructorNode = nonPrivateConstructors.find { it.getAnnotations(makeCached(Inject)) }
                if (!constructorNode) {
                    constructorNode = nonPrivateConstructors.find { Modifier.isPublic(it.modifiers) }
                }
            }
            constructorNode
        }

        private void populateParameterData(Parameter[] parameters, Map<String, Object> paramsToType, Map<String, AnnotationMetadata> anntationMetadata, Map<String, Map<String, Object>> genericTypeMap) {
            for (param in parameters) {
                String parameterName = param.name

                paramsToType.put(parameterName, resolveParameterType(param))

                anntationMetadata.put(parameterName, AstAnnotationUtils.getAnnotationMetadata(param))

                genericTypeMap.put(parameterName, resolveGenericTypes(param))
            }
        }

        private List<ConstructorNode> findNonPrivateConstructors(List<ConstructorNode> constructorNodes) {
            List<ConstructorNode> nonPrivateConstructors = []
            for (node in constructorNodes) {
                if (!Modifier.isPrivate(node.modifiers)) {
                    nonPrivateConstructors.add(node)
                }
            }
            return nonPrivateConstructors
        }

        private void visitConfigurationBuilder(AnnotationMetadata annotationMetadata, ClassNode classNode, BeanDefinitionVisitor writer) {
            Boolean allowZeroArgs = annotationMetadata.getValue(ConfigurationBuilder.class, "allowZeroArgs", Boolean.class).orElse(false)
            List<String> prefixes = Arrays.asList(annotationMetadata.getValue(ConfigurationBuilder.class, "prefixes", String[].class).orElse(["set"] as String[]))
            String configurationPrefix = annotationMetadata.getValue(ConfigurationBuilder.class, "configurationPrefix", String.class).orElse("")

            PublicMethodVisitor visitor = new PublicMethodVisitor(sourceUnit) {
                @Override
                void accept(ClassNode cn, MethodNode method) {
                    Parameter[] params = method.getParameters()
                    String methodName = method.getName()
                    String prefix = getMethodPrefix(methodName)
                    Parameter paramType = params.size() == 1 ? params[0] : null
                    Object expectedType = paramType != null ? AstGenericUtils.resolveTypeReference(paramType.type) : null;
                    writer.visitConfigBuilderMethod(
                        prefix,
                        configurationPrefix,
                        AstGenericUtils.resolveTypeReference(method.getReturnType()),
                        methodName,
                        expectedType,
                        paramType != null ? resolveGenericTypes(paramType) : null
                    )
                }

                @Override
                protected boolean isAcceptable(MethodNode node) {
                    int paramCount = node.getParameters().size()
                    return (paramCount == 1 || allowZeroArgs && paramCount == 0) && super.isAcceptable(node) && isPrefixedWith(node)
                }

                private boolean isPrefixedWith(MethodNode node) {
                    String name = node.getName()
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

            visitor.accept(classNode)
        }
    }
}
