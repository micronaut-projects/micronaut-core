/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.ast.groovy

import edu.umd.cs.findbugs.annotations.Nullable
import groovy.transform.CompileDynamic
import io.micronaut.aop.Adapter
import io.micronaut.ast.groovy.utils.AstClassUtils
import io.micronaut.ast.groovy.utils.ExtendedParameter
import io.micronaut.ast.groovy.visitor.AbstractGroovyElement
import io.micronaut.ast.groovy.visitor.GroovyClassElement
import io.micronaut.ast.groovy.visitor.GroovyPackageElement
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.DefaultScope
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.util.CollectionUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.annotation.DefaultAnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.configuration.ConfigurationMetadata
import io.micronaut.inject.configuration.PropertyMetadata
import io.micronaut.inject.processing.JavaModelUtils
import io.micronaut.inject.writer.DirectoryClassWriterOutputVisitor
import io.micronaut.inject.writer.GeneratedFile
import io.micronaut.inject.writer.OriginatingElements

import javax.inject.Named
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.function.Predicate

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
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Configuration
import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Internal
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.util.ArrayUtils
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AnnotationMetadataReference
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder
import io.micronaut.inject.processing.ProcessedTypes
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
// IMPORTANT NOTE: This transform runs in phase CANONICALIZATION so it runs after TypeElementVisitorTransform
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class InjectTransform implements ASTTransformation, CompilationUnitAware {

    public static final String ANN_VALID = "javax.validation.Valid"
    public static final String ANN_CONSTRAINT = "javax.validation.Constraint"
    public static final String ANN_CONFIGURATION_ADVICE = "io.micronaut.runtime.context.env.ConfigurationAdvice"
    public static final String ANN_VALIDATED = "io.micronaut.validation.Validated"
    private static final Predicate<AnnotationMetadata> IS_CONSTRAINT = (Predicate<AnnotationMetadata>) { AnnotationMetadata am ->
        am.hasStereotype(InjectTransform.ANN_CONSTRAINT) || am.hasStereotype(InjectTransform.ANN_VALID)
    }
    CompilationUnit unit
    ConfigurationMetadataBuilder<ClassNode> configurationMetadataBuilder

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        configurationMetadataBuilder = new GroovyConfigurationMetadataBuilder(source, unit)
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
                if (AstAnnotationUtils.hasStereotype(source, unit, packageNode, Configuration)) {
                    def annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(source, unit, packageNode)
                    BeanConfigurationWriter writer = new BeanConfigurationWriter(
                            classNode.packageName,
                            new GroovyPackageElement(source, unit, packageNode, annotationMetadata),
                            annotationMetadata
                    )
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
                    if (AstAnnotationUtils.hasStereotype(source, unit, classNode, InjectVisitor.INTRODUCTION_TYPE) ||
                            AstAnnotationUtils.hasStereotype(source, unit, classNode, ConfigurationReader.class)) {
                        InjectVisitor injectVisitor = new InjectVisitor(source, unit, classNode, configurationMetadataBuilder)
                        injectVisitor.visitClass(classNode)
                        beanDefinitionWriters.putAll(injectVisitor.beanDefinitionWriters)
                    }
                } else {
                    InjectVisitor injectVisitor = new InjectVisitor(source, unit, classNode, configurationMetadataBuilder)
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
                if (beanClassNode instanceof ClassNode) {
                    ClassNode cn = (ClassNode) beanClassNode
                    ClassNode providerType = AstGenericUtils.resolveInterfaceGenericType(cn, Provider.class)

                    if (providerType != null) {
                        beanTypeName = providerType.name
                    }
                }

                BeanDefinitionReferenceWriter beanReferenceWriter = new BeanDefinitionReferenceWriter(
                        beanTypeName,
                        beanDefWriter
                )

                beanReferenceWriter.setRequiresMethodProcessing(beanDefWriter.requiresMethodProcessing())
                beanReferenceWriter.setContextScope(AstAnnotationUtils.hasStereotype(source, unit, beanClassNode, Context))
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
                        OutputStream visitClass(String classname, @Nullable Element originatingElement) throws IOException {
                            ByteArrayOutputStream stream = new ByteArrayOutputStream()
                            classStreams.put(classname, stream)
                            return stream
                        }

                        @Override
                        OutputStream visitClass(String classname, Element... originatingElements) throws IOException {
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
                        Optional<GeneratedFile> visitGeneratedFile(String path) {
                            return Optional.empty()
                        }

                        @Override
                        Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
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
                e.printStackTrace(System.err)
            }
        }
        if (!beanDefinitionWriters.isEmpty()) {

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
        final ClassElement originatingElement
        final AnnotationMetadata annotationMetadata
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
            this.annotationMetadata = annotationMetadata
            this.originatingElement = AbstractGroovyElement.toClassElement(sourceUnit, compilationUnit, concreteClass, annotationMetadata)
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
            AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, node)
            boolean isInterface = node.isInterface()
            if (isConfigurationProperties && isInterface) {
                annotationMetadata = addAnnotation(
                        annotationMetadata,
                        InjectTransform.ANN_CONFIGURATION_ADVICE
                )
            }
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


                AopProxyWriter aopProxyWriter = new AopProxyWriter(
                        packageName,
                        beanClassName,
                        isInterface,
                        originatingElement,
                        annotationMetadata,
                        interfaceTypes,
                        interceptorTypes
                )
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
            PublicMethodVisitor publicMethodVisitor = new PublicAbstractMethodVisitor(source, unit) {

                @Override
                protected boolean isAcceptableMethod(MethodNode methodNode) {
                    return super.isAcceptableMethod(methodNode) || AstAnnotationUtils.getAnnotationMetadata(source, unit, methodNode).hasDeclaredStereotype(AROUND_TYPE)
                }

                @Override
                void accept(ClassNode classNode, MethodNode methodNode) {
                    Map<String, Object> targetMethodParamsToType = [:]
                    Map<String, Object> targetGenericParams = [:]
                    Map<String, AnnotationMetadata> targetAnnotationMetadata = [:]
                    Map<String, Map<String, Object>> targetMethodGenericTypeMap = [:]

                    Map<String, ClassNode> boundTypes = AstGenericUtils.createGenericsSpec(classNode)

                    if (!classNode.isPrimaryClassNode()) {
                        AstGenericUtils.createGenericsSpec(methodNode, boundTypes)
                    }
                    Object resolvedReturnType = AstGenericUtils.resolveTypeReference(methodNode.returnType, boundTypes)
                    def owningType = AstGenericUtils.resolveTypeReference(methodNode.declaringClass)
                    def returnType = resolveReturnType(classNode, methodNode, boundTypes)

                    Map<String, Object> resolvedGenericTypes = AstGenericUtils.buildGenericTypeInfo(
                            methodNode.returnType,
                            boundTypes
                    )
                    populateParameterData(
                        methodNode,
                        targetMethodParamsToType,
                        targetGenericParams,
                        targetAnnotationMetadata,
                        targetMethodGenericTypeMap,
                        boundTypes,
                        false
                    )


                    AnnotationMetadata annotationMetadata
                    if (AstAnnotationUtils.isAnnotated(node.name, methodNode) || AstAnnotationUtils.hasAnnotation(methodNode, Override)) {
                        annotationMetadata = AstAnnotationUtils.newBuilder(source, unit).buildForParent(node.name, node, methodNode)
                    } else {
                        annotationMetadata = new AnnotationMetadataReference(
                                aopProxyWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                typeAnnotationMetadata
                        )
                    }

                    if (!annotationMetadata.hasStereotype("io.micronaut.validation.Validated") &&
                            isDeclaredBean) {
                        boolean hasConstraint
                        for (Parameter p: methodNode.getParameters()) {
                            AnnotationMetadata parameterMetadata = AstAnnotationUtils.getAnnotationMetadata(source, unit, p)
                            if (IS_CONSTRAINT.test(parameterMetadata)) {
                                hasConstraint = true
                                break
                            }
                        }
                        if (hasConstraint) {
                            annotationMetadata = addValidated(annotationMetadata)
                        }
                    }

                    if (isConfigurationProperties && methodNode.isAbstract()) {
                        if (!aopProxyWriter.isValidated()) {
                            aopProxyWriter.setValidated(IS_CONSTRAINT.test(annotationMetadata))
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
                                annotationMetadata,
                                propertyMetadata
                        )

                        final ClassNode typeElement = !ClassUtils.isJavaBasicType(propertyType) ? methodNode.returnType : null
                        if (typeElement != null && AstAnnotationUtils.hasStereotype(source, unit, typeElement, Scope.class)) {
                            annotationMetadata = addBeanConfigAdvise(annotationMetadata)
                        } else {
                            annotationMetadata = addAnnotation(annotationMetadata, ANN_CONFIGURATION_ADVICE)
                        }

                    }

                    if (AstAnnotationUtils.hasStereotype(source, unit, methodNode, AROUND_TYPE)) {
                        Object[] interceptorTypeReferences = annotationMetadata.getAnnotationNamesByStereotype(Around).toArray()
                        aopProxyWriter.visitInterceptorTypes(interceptorTypeReferences)
                    }

                    if (methodNode.isAbstract()) {
                        aopProxyWriter.visitIntroductionMethod(
                                owningType,
                                returnType,
                                resolvedReturnType,
                                resolvedGenericTypes,
                                methodNode.name,
                                targetMethodParamsToType,
                                targetGenericParams,
                                targetAnnotationMetadata,
                                targetMethodGenericTypeMap,
                                annotationMetadata
                        )
                    } else {
                        aopProxyWriter.visitAroundMethod(
                                owningType,
                                returnType,
                                resolvedReturnType,
                                resolvedGenericTypes,
                                methodNode.name,
                                targetMethodParamsToType,
                                targetGenericParams,
                                targetAnnotationMetadata,
                                targetMethodGenericTypeMap,
                                annotationMetadata,
                                methodNode.declaringClass.isInterface(),
                                false
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
                            AnnotationValue.builder(ANN_CONFIGURATION_ADVICE).member("bean", true).build()
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
            if (methodNode.isSynthetic() || methodNode.name.contains('$')) return

            String methodName = methodNode.name
            ClassNode declaringClass = methodNode.declaringClass
            AnnotationMetadata methodAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, methodNode)
            if (isFactoryClass && !isConstructor && methodAnnotationMetadata.hasDeclaredStereotype(Bean, Scope)) {
                methodAnnotationMetadata = new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit).buildForParent(methodNode.returnType, methodNode, true)
                if (annotationMetadata.hasDeclaredStereotype(Around)) {
                    visitExecutableMethod(declaringClass, methodNode, methodAnnotationMetadata, methodName, methodNode.isPublic())
                }

                ClassNode producedType = methodNode.returnType
                String beanDefinitionPackage = concreteClass.packageName
                String upperCaseMethodName = NameUtils.capitalize(methodNode.getName())
                String factoryMethodBeanDefinitionName =
                        beanDefinitionPackage + '.$' + concreteClass.nameWithoutPackage + '$' + upperCaseMethodName + factoryMethodIndex.getAndIncrement() + "Definition"

                BeanDefinitionWriter beanMethodWriter = new BeanDefinitionWriter(
                        producedType.packageName,
                        producedType.nameWithoutPackage,
                        factoryMethodBeanDefinitionName,
                        producedType.name,
                        producedType.isInterface(),
                        originatingElement,
                        methodAnnotationMetadata
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

                    typeArguments.put(AstGenericUtils.resolveTypeReference(returnType).toString(), boundTypes)
                    AstGenericUtils.populateTypeArguments(returnType, typeArguments)
                    beanMethodWriter.visitTypeArguments(typeArguments)
                }

                Map<String, Object> paramsToType = [:]
                Map<String, Object> paramsGenerics = [:]
                Map<String, AnnotationMetadata> argumentAnnotationMetadata = [:]
                Map<String, Map<String, Object>> genericTypeMap = [:]
                populateParameterData(
                        methodNode,
                        paramsToType,
                        paramsGenerics,
                        argumentAnnotationMetadata,
                        genericTypeMap,
                        genericsSpec,
                        false
                )

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
                            interceptorTypeReferences)
                    if (producedType.isInterface()) {
                        proxyWriter.visitBeanDefinitionConstructor(
                                AnnotationMetadata.EMPTY_METADATA,
                                false
                        )
                    } else {
                        populateProxyWriterConstructor(producedType, proxyWriter)
                    }
                    SourceUnit source = this.sourceUnit
                    CompilationUnit unit = this.compilationUnit
                    new PublicMethodVisitor(source) {
                        @Override
                        void accept(ClassNode classNode, MethodNode targetBeanMethodNode) {
                            Map<String, Object> targetMethodParamsToType = [:]
                            Map<String, Object> targetGenericParams = [:]
                            Map<String, AnnotationMetadata> targetAnnotationMetadata = [:]
                            Map<String, Map<String, Object>> targetMethodGenericTypeMap = [:]

                            Map<String, Map<String, ClassNode>> genericInfo = AstGenericUtils.buildAllGenericElementInfo(classNode, new GroovyVisitorContext(source, unit))

                            Map<String, ClassNode> declaringTypeGenericInfo = genericInfo.get(methodNode.declaringClass.name)
                            if (declaringTypeGenericInfo == null) {
                                declaringTypeGenericInfo = Collections.emptyMap()
                            }

                            Object resolvedReturnType = AstGenericUtils.resolveTypeReference(targetBeanMethodNode.returnType, declaringTypeGenericInfo)
                            Object returnTypeReference = resolveReturnType(classNode, targetBeanMethodNode, declaringTypeGenericInfo)

                            Map<String, Object> resolvedGenericTypes = AstGenericUtils.buildGenericTypeInfo(methodNode.returnType, declaringTypeGenericInfo)


                            populateParameterData(
                                targetBeanMethodNode,
                                targetMethodParamsToType,
                                targetGenericParams,
                                targetAnnotationMetadata,
                                targetMethodGenericTypeMap,
                                declaringTypeGenericInfo,
                                false)
                            AnnotationMetadata annotationMetadata
                            if (AstAnnotationUtils.isAnnotated(producedType.name, methodNode)) {
                                annotationMetadata = AstAnnotationUtils.newBuilder(source, unit)
                                        .buildForParent(
                                        producedType.name, methodNode, targetBeanMethodNode)
                            } else {
                                annotationMetadata = new AnnotationMetadataReference(
                                        beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                        methodAnnotationMetadata
                                )
                            }

                            proxyWriter.visitAroundMethod(
                                    AstGenericUtils.resolveTypeReference(targetBeanMethodNode.declaringClass),
                                    returnTypeReference,
                                    resolvedReturnType,
                                    resolvedGenericTypes,
                                    targetBeanMethodNode.name,
                                    targetMethodParamsToType,
                                    targetGenericParams,
                                    targetAnnotationMetadata,
                                    targetMethodGenericTypeMap,
                                    annotationMetadata,
                                    targetBeanMethodNode.declaringClass.isInterface(),
                                    false
                            )
                        }
                    }.accept(returnType)
                    beanDefinitionWriters.put(new AnnotatedNode(), proxyWriter)

                }
                Optional<String> preDestroy = methodAnnotationMetadata.getValue(Bean, "preDestroy", String.class)
                if (preDestroy.isPresent()) {
                    String destroyMethodName = preDestroy.get()
                    MethodNode destroyMethod = producedType.getMethod(destroyMethodName)
                    if (destroyMethod != null) {
                        beanMethodWriter.visitPreDestroyMethod(
                                destroyMethod.declaringClass.name,
                                AstGenericUtils.resolveTypeReference(destroyMethod.returnType, genericsSpec),
                                destroyMethodName
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

                        Map<String, Object> paramsToType = [:]
                        Map<String, AnnotationMetadata> argumentAnnotationMetadata = [:]
                        Map<String, Map<String, Object>> genericTypeMap = [:]
                        populateParameterData(
                                methodNode,
                                paramsToType,
                                paramsToType,
                                argumentAnnotationMetadata,
                                genericTypeMap,
                                Collections.emptyMap(),
                                false
                        )

                        if (isDeclaredBean && methodAnnotationMetadata.hasStereotype(ProcessedTypes.POST_CONSTRUCT)) {
                            defineBeanDefinition(concreteClass)
                            def beanWriter = getBeanWriter()
                            if (aopProxyWriter instanceof AopProxyWriter && !((AopProxyWriter)aopProxyWriter).isProxyTarget()) {
                                beanWriter = aopProxyWriter
                            }
                            beanWriter.visitPostConstructMethod(
                                    AstGenericUtils.resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodName,
                                    paramsToType,
                                    argumentAnnotationMetadata,
                                    genericTypeMap,
                                    methodAnnotationMetadata)
                        } else if (isDeclaredBean && methodAnnotationMetadata.hasStereotype(ProcessedTypes.PRE_DESTROY)) {
                            defineBeanDefinition(concreteClass)
                            def beanWriter = getBeanWriter()
                            if (aopProxyWriter instanceof AopProxyWriter && !((AopProxyWriter)aopProxyWriter).isProxyTarget()) {
                                beanWriter = aopProxyWriter
                            }
                            beanWriter.visitPreDestroyMethod(
                                    AstGenericUtils.resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodName,
                                    paramsToType,
                                    argumentAnnotationMetadata,
                                    genericTypeMap,
                                    methodAnnotationMetadata)
                        } else if (methodAnnotationMetadata.hasStereotype(Inject.class)) {
                            defineBeanDefinition(concreteClass)
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
                boolean isExecutable = ((isExecutableType && isPublic) || methodAnnotationMetadata.hasStereotype(Executable) || methodAnnotationMetadata.hasStereotype(AROUND_TYPE)) && !hasInvalidModifiers
                if (isDeclaredBean && isExecutable) {
                    visitExecutableMethod(declaringClass, methodNode, methodAnnotationMetadata, methodName, isPublic)
                } else if (isConfigurationProperties && isPublic) {
                    if (NameUtils.isSetterName(methodNode.name) && methodNode.parameters.length == 1) {
                        String propertyName = NameUtils.getPropertyNameForSetter(methodNode.name)
                        Parameter parameter = methodNode.parameters[0]

                        if (methodAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                            getBeanWriter().visitConfigBuilderMethod(
                                    parameter.type.name,
                                    NameUtils.getterNameFor(propertyName),
                                    methodAnnotationMetadata,
                                    configurationMetadataBuilder,
                                    parameter.type.interface)
                            try {
                                visitConfigurationBuilder(declaringClass, methodAnnotationMetadata, parameter.type, getBeanWriter())
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
                                    parameter.type.name,
                                    propertyName,
                                    null,
                                    null
                            )

                            methodAnnotationMetadata = addPropertyMetadata(methodAnnotationMetadata, propertyMetadata)

                            getBeanWriter().visitSetterValue(
                                    AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodAnnotationMetadata,
                                    false,
                                    resolveParameterType(parameter),
                                    methodNode.name,
                                    resolveGenericTypes(parameter),
                                    AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, parameter),
                                    true
                            )
                        }
                    } else if (NameUtils.isGetterName(methodNode.name)) {
                        if (!getBeanWriter().isValidated()) {
                            getBeanWriter().setValidated(IS_CONSTRAINT.test(methodAnnotationMetadata))
                        }
                    }
                } else if (isPublic) {
                    def sourceUnit = sourceUnit
                    def compilationUnit = this.compilationUnit
                    final boolean isConstrained = isDeclaredBean &&
                            methodNode.getParameters()
                                    .any { Parameter p ->
                                        AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, p)
                                        IS_CONSTRAINT.test(annotationMetadata)
                            }
                    if (isConstrained) {
                        visitExecutableMethod(declaringClass, methodNode, methodAnnotationMetadata, methodName, isPublic)
                    }
                }
            }
        }

        private AnnotationMetadata addPropertyMetadata(AnnotationMetadata methodAnnotationMetadata, PropertyMetadata propertyMetadata) {
            DefaultAnnotationMetadata.mutateMember(
                    methodAnnotationMetadata,
                    PropertySource.class.getName(),
                    AnnotationMetadata.VALUE_MEMBER,
                    Collections.singletonList(
                            new AnnotationValue(
                                    Property.class.getName(),
                                    Collections.singletonMap(
                                            (CharSequence) "name",
                                            (Object) propertyMetadata.getPath()
                                    )
                            )
                    )
            )
        }

        private void visitExecutableMethod(
                ClassNode declaringClass,
                MethodNode methodNode,
                AnnotationMetadata methodAnnotationMetadata,
                String methodName, boolean isPublic) {
            if (declaringClass != ClassHelper.OBJECT_TYPE) {

                boolean isOwningClass = declaringClass == concreteClass

                Map<String, Map<String, ClassNode>> genericInfo = AstGenericUtils.buildAllGenericElementInfo(concreteClass, new GroovyVisitorContext(sourceUnit, compilationUnit))

                Map<String, ClassNode> declaringTypeGenericInfo = genericInfo.get(methodNode.declaringClass.name)
                if (declaringTypeGenericInfo == null) {
                    declaringTypeGenericInfo = Collections.emptyMap()
                }

                List<Parameter> resolvedParameters = []

                for (Parameter p: methodNode.parameters) {
                    if (p.type.isGenericsPlaceHolder()) {
                        String name = p.type.genericsTypes[0].name
                        resolvedParameters.add(new Parameter(declaringTypeGenericInfo.get(name), p.name))
                    } else {
                        resolvedParameters.add(p)
                    }
                }

                boolean isParent = declaringClass != concreteClass
                MethodNode overriddenMethod = isParent ? concreteClass.getMethod(methodName, resolvedParameters as Parameter[]) : methodNode
                if (!isOwningClass && overriddenMethod != null && overriddenMethod.declaringClass != declaringClass) {
                    return
                }

                Map<String, Object> returnTypeGenerics = AstGenericUtils.buildGenericTypeInfo(methodNode.returnType, declaringTypeGenericInfo)

                Map<String, Object> paramsToType = [:]
                Map<String, Object> genericParams = [:]
                Map<String, AnnotationMetadata> argumentAnnotationMetadata = [:]
                Map<String, Map<String, Object>> genericTypeMap = [:]
                populateParameterData(
                        methodNode,
                        paramsToType,
                        genericParams,
                        argumentAnnotationMetadata,
                        genericTypeMap,
                        declaringTypeGenericInfo,
                        false
                )

                defineBeanDefinition(concreteClass)

                boolean preprocess = methodAnnotationMetadata.booleanValue(Executable.class, "processOnStartup").orElse(false)
                if (preprocess) {
                    getBeanWriter().setRequiresMethodProcessing(true)
                }
                final boolean hasConstraints = argumentAnnotationMetadata.values().stream().anyMatch({ am ->
                    IS_CONSTRAINT.test(am)
                })

                if (hasConstraints) {
                    if (!methodAnnotationMetadata.hasStereotype("io.micronaut.validation.Validated")) {
                        methodAnnotationMetadata = addValidated(methodAnnotationMetadata)
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
                                    AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    returnTypeGenerics,
                                    methodName,
                                    paramsToType,
                                    genericParams,
                                    argumentAnnotationMetadata,
                                    genericTypeMap,
                                    methodAnnotationMetadata,
                                    methodNode.declaringClass.isInterface(),
                                    false
                            )

                            executorMethodAdded = true
                        }
                    }
                }

                if (!executorMethodAdded) {
                    getBeanWriter().visitExecutableMethod(
                            AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                            AstGenericUtils.resolveTypeReference(methodNode.returnType),
                            AstGenericUtils.resolveTypeReference(methodNode.returnType, declaringTypeGenericInfo),
                            returnTypeGenerics,
                            methodName,
                            paramsToType,
                            genericParams,
                            argumentAnnotationMetadata,
                            genericTypeMap,
                            methodAnnotationMetadata,
                            methodNode.declaringClass.isInterface(),
                            false
                    )
                }
            }
        }

        @CompileDynamic
        private AnnotationMetadata addValidated(AnnotationMetadata methodAnnotationMetadata) {
            return addAnnotation(methodAnnotationMetadata, "io.micronaut.validation.Validated")
        }

        @CompileDynamic
        private AnnotationMetadata addAnnotation(AnnotationMetadata methodAnnotationMetadata, String annotationName) {
            methodAnnotationMetadata = new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit).annotate(
                    methodAnnotationMetadata,
                    AnnotationValue.builder(annotationName).build()
            )
            return methodAnnotationMetadata
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
                    populateParameterData(constructorNode,
                                          constructorParamsToType,
                                           constructorParamsToType,
                                          constructorArgumentMetadata,
                                          constructorGenericTypeMap,
                                          Collections.emptyMap(),
                            false
                    )
                    def constructorMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, constructorNode)

                    proxyWriter.visitBeanDefinitionConstructor(
                            constructorMetadata,
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

            if ((isInject || isValue) && declaringClass.getProperty(fieldNode.getName()) == null) {
                defineBeanDefinition(concreteClass)
                if (!fieldNode.isStatic()) {

                    boolean isPrivate = Modifier.isPrivate(modifiers)
                    boolean requiresReflection = isPrivate || isInheritedAndNotPublic(fieldNode, fieldNode.declaringClass, modifiers)
                    if (!getBeanWriter().isValidated()) {
                        getBeanWriter().setValidated(IS_CONSTRAINT.test(fieldAnnotationMetadata))
                    }
                    String fieldName = fieldNode.name
                    Object fieldType = AstGenericUtils.resolveTypeReference(fieldNode.type)
                    if (isValue) {
                        if (isConfigurationProperties && fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                            if(requiresReflection) {
                                // Using the field would throw a IllegalAccessError, use the method instead
                                String fieldGetterName = NameUtils.getterNameFor(fieldName)
                                MethodNode getterMethod = declaringClass.methods?.find { it.name == fieldGetterName}
                                if(getterMethod != null) {
                                    getBeanWriter().visitConfigBuilderMethod(fieldType, getterMethod.name, fieldAnnotationMetadata, configurationMetadataBuilder, fieldNode.type.interface)
                                } else {
                                    addError("ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.", fieldNode)
                                }
                            } else {
                                getBeanWriter().visitConfigBuilderField(fieldType, fieldName, fieldAnnotationMetadata, configurationMetadataBuilder, fieldNode.type.interface)
                            }
                            try {
                                visitConfigurationBuilder(declaringClass, fieldAnnotationMetadata, fieldNode.type, getBeanWriter())
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
                                fieldAnnotationMetadata = DefaultAnnotationMetadata.mutateMember(
                                        fieldAnnotationMetadata,
                                        PropertySource.class.getName(),
                                        AnnotationMetadata.VALUE_MEMBER,
                                        Collections.singletonList(
                                                new AnnotationValue(
                                                        Property.class.getName(),
                                                        Collections.singletonMap(
                                                                (CharSequence) "name",
                                                                (Object) propertyMetadata.getPath()
                                                        )
                                                )
                                        )
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

        Map<String, Object> resolveGenericTypes(
                Parameter parameter,
                Map<String, ClassNode> boundTypes = Collections.emptyMap()) {
            ClassNode parameterType = parameter.type
            GenericsType[] genericsTypes = parameterType.genericsTypes
            if (genericsTypes != null && genericsTypes.length > 0) {
                return AstGenericUtils.buildGenericTypeInfo(parameterType, boundTypes)
            } else if (parameterType.isArray()) {
                Map<String, Object> genericTypeList = [:]
                genericTypeList.put('E', AstGenericUtils.resolveTypeReference(parameterType.componentType, boundTypes))
                return genericTypeList
            }
        }

        @Override
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
                    getBeanWriter().setValidated(IS_CONSTRAINT.test(fieldAnnotationMetadata))
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
                                configurationMetadataBuilder,
                                fieldNode.type.interface)
                        try {
                            visitConfigurationBuilder(declaringClass, fieldAnnotationMetadata, fieldNode.type, getBeanWriter())
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
                                    declaringClass,
                                    propertyNode.type.name,
                                    propertyNode.name,
                                    null, // TODO: fix groovy doc support
                                    null
                            )
                            fieldAnnotationMetadata = DefaultAnnotationMetadata.mutateMember(
                                    fieldAnnotationMetadata,
                                    PropertySource.class.getName(),
                                    AnnotationMetadata.VALUE_MEMBER,
                                    Collections.singletonList(
                                            new AnnotationValue(
                                                    Property.class.getName(),
                                                    Collections.singletonMap(
                                                            (CharSequence) "name",
                                                            (Object) propertyMetadata.getPath()
                                                    )
                                            )
                                    )
                            )
                        }
                        getBeanWriter().visitSetterValue(
                                AstGenericUtils.resolveTypeReference(declaringClass),
                                void.class,
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
                    def propertyType = AstGenericUtils.resolveTypeReference(propertyNode.type)
                    Map<String, Object> resolvedArguments =
                            [(propertyName): propertyType]

                    AnnotationMetadata fieldMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, propertyNode.field)

                    Map<String, AnnotationMetadata> resolvedAnnotationMetadata
                    def emptyMap = Collections.emptyMap()
                    if (fieldMetadata != null) {
                        resolvedAnnotationMetadata = [(propertyName): fieldMetadata]
                    } else {
                        resolvedAnnotationMetadata = emptyMap
                    }

                    aopWriter.visitAroundMethod(
                            propertyNode.getDeclaringClass().name,
                            void.class,
                            void.class,
                            emptyMap,
                            getSetterName(propertyName),
                            resolvedArguments,
                            resolvedArguments,
                            resolvedAnnotationMetadata,
                            resolvedGenericTypes,
                            fieldAnnotationMetadata,
                            propertyNode.declaringClass.isInterface(),
                            false
                    )

                    // also visit getter to ensure proxying

                    aopWriter.visitAroundMethod(
                            propertyNode.getDeclaringClass().name,
                            propertyType,
                            propertyType,
                            emptyMap,
                            getGetterName(propertyNode),
                            emptyMap,
                            emptyMap,
                            emptyMap,
                            emptyMap,
                            fieldAnnotationMetadata,
                            propertyNode.getDeclaringClass().isInterface(),
                            false
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
                ClassNode providerGenericType = AstGenericUtils.resolveInterfaceGenericType(classNode, Provider)
                boolean isProvider = providerGenericType != null
                AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, classNode)

                if (annotationMetadata.hasStereotype(groovy.lang.Singleton)) {
                    addError("Class annotated with groovy.lang.Singleton instead of javax.inject.Singleton. Import javax.inject.Singleton to use Micronaut Dependency Injection.", classNode)
                }
                if (configurationMetadata != null) {
                    String existingPrefix = annotationMetadata.getValue(
                            ConfigurationReader.class,
                            "prefix", String.class)
                            .orElse("")

                    annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                            annotationMetadata,
                            ConfigurationReader.class.getName(),
                            "prefix",
                            StringUtils.isNotEmpty(existingPrefix) ? existingPrefix + "." + configurationMetadata.getName() : configurationMetadata.getName()
                    )
                }
                if (isProvider) {
                    beanWriter = new BeanDefinitionWriter(
                            classNode.packageName,
                            classNode.nameWithoutPackage,
                            providerGenericType.name,
                            classNode.isInterface(),
                            originatingElement,
                            annotationMetadata
                    )
                } else {

                    beanWriter = new BeanDefinitionWriter(
                            classNode.packageName,
                            classNode.nameWithoutPackage,
                            originatingElement,
                            annotationMetadata
                    )
                }

                visitTypeArguments(classNode, (BeanDefinitionWriter) beanWriter)

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

                        def constructorMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, constructorNode)
                        final boolean isConstructBinding = constructorMetadata.hasDeclaredStereotype(ConfigurationInject.class)
                        if (isConstructBinding) {
                            this.configurationMetadata = configurationMetadataBuilder.visitProperties(
                                    concreteClass,
                                    null)
                        }
                        Map<String, Object> paramsToType = [:]
                        Map<String, AnnotationMetadata> qualifierTypes = [:]
                        Map<String, Map<String, Object>> genericTypeMap = [:]
                        populateParameterData(
                                constructorNode,
                                paramsToType,
                                paramsToType,
                                qualifierTypes,
                                genericTypeMap,
                                Collections.emptyMap(),
                                isConstructBinding
                        )
                        beanWriter.visitBeanDefinitionConstructor(
                                constructorMetadata,
                                constructorNode.isPrivate(),
                                paramsToType,
                                qualifierTypes,
                                genericTypeMap
                        )
                        beanWriter.setValidated(
                                qualifierTypes.values().any { AnnotationMetadata am -> InjectTransform.IS_CONSTRAINT.test(am) }
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
                            ArrayUtils.EMPTY_OBJECT_ARRAY
                    )

                    aopProxyWriter.visitBeanDefinitionConstructor(methodAnnotationMetadata, false)

                    beanDefinitionWriters.put(ClassHelper.make(packageName + '.' + beanClassName), aopProxyWriter)


                    GenericsType[] typeArguments = typeToImplement.getGenericsTypes()
                    Map<String, ClassNode> typeVariables = new HashMap<>(typeArguments?.size() ?: 1)

                    for (GenericsType typeArgument : typeArguments) {
                        typeVariables.put(typeArgument.name, typeArgument.type)
                    }

                    InjectVisitor thisVisitor = this
                    SourceUnit source = this.sourceUnit
                    CompilationUnit unit = this.compilationUnit
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
                                            typeData
                                    )
                                }

                                Map<String, Object> targetMethodParamsToType = [:]
                                Map<String, Object> targetMethodGenericParams = [:]
                                Map<String, AnnotationMetadata> targetAnnotationMetadata = [:]
                                Map<String, Map<String, Object>> targetMethodGenericTypeMap = [:]

                                if (!classNode.isPrimaryClassNode()) {
                                    AstGenericUtils.createGenericsSpec(targetMethod, boundTypes)
                                }
                                Object resolvedReturnType = AstGenericUtils.resolveTypeReference(targetMethod.returnType, boundTypes)
                                Map<String, Object> resolvedGenericTypes = AstGenericUtils.buildGenericTypeInfo(
                                        targetMethod.returnType,
                                        boundTypes
                                )
                                populateParameterData(
                                        targetMethod,
                                        targetMethodParamsToType,
                                        targetMethodGenericParams,
                                        targetAnnotationMetadata,
                                        targetMethodGenericTypeMap,
                                        boundTypes,
                                        false
                                )

                                AnnotationClassValue[] adaptedArgumentTypes = new AnnotationClassValue[sourceParameters.length]
                                int j = 0
                                for (Parameter ve in sourceParameters) {
                                    Object r = AstGenericUtils.resolveTypeReference(ve.type, boundTypes)
                                    if (r instanceof Class) {
                                        adaptedArgumentTypes[j] = new AnnotationClassValue((Class) r)
                                    } else {
                                        adaptedArgumentTypes[j] = new AnnotationClassValue(r.toString())
                                    }
                                    j++
                                }

                                def values = CollectionUtils.mapOf(
                                        Adapter.InternalAttributes.ADAPTED_BEAN, new AnnotationClassValue<>(concreteClass.name),
                                        Adapter.InternalAttributes.ADAPTED_METHOD, method.name,
                                        Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES, adaptedArgumentTypes
                                )


                                String qualifier = AstAnnotationUtils.getAnnotationMetadata(source, unit, concreteClass).getValue(Named.class, String.class).orElse(null)
                                if (StringUtils.isNotEmpty(qualifier)) {
                                    values.put(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier)
                                }
                                AnnotationMetadata annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                                        methodAnnotationMetadata,
                                        Adapter.class.getName(),
                                        values
                                )

                                aopProxyWriter.visitAroundMethod(
                                        AstGenericUtils.resolveTypeReference(targetMethod.declaringClass),
                                        resolveReturnType(classNode, targetMethod, boundTypes),
                                        resolvedReturnType,
                                        resolvedGenericTypes,
                                        targetMethod.name,
                                        targetMethodParamsToType,
                                        targetMethodGenericParams,
                                        targetAnnotationMetadata,
                                        targetMethodGenericTypeMap,
                                        annotationMetadata,
                                        targetMethod.declaringClass.isInterface(),
                                        false
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
            beanDefinitionWriter.visitTypeArguments(
                    typeArguments
            )
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

        private void populateParameterData(
                MethodNode methodNode,
                Map<String, Object> paramsToType,
                Map<String, Object> genericParams,
                Map<String, AnnotationMetadata> anntationMetadata,
                Map<String, Map<String, Object>> genericTypeMap,
                Map<String, ClassNode> boundTypes,
                boolean isConstructBinding) {
            for (Parameter param in methodNode.parameters) {
                String parameterName = param.name

                paramsToType.put(parameterName, resolveParameterType(param))
                def typeRef = AstGenericUtils.resolveTypeReference(param.type, boundTypes)
                genericParams.put(parameterName, typeRef)

                def annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, new ExtendedParameter(methodNode, param))

                if (isConstructBinding) {
                    if (!annotationMetadata.hasStereotype(io.micronaut.context.annotation.Parameter, Property, Value)) {
                        if (!AstAnnotationUtils.hasStereotype(sourceUnit, compilationUnit, param.type, Scope)) {
                            def propertyMetadata = configurationMetadataBuilder.visitProperty(
                                    typeRef instanceof Class ? ((Class) typeRef).name : typeRef.toString(),
                                    parameterName,
                                    null,
                                    null
                            )

                            annotationMetadata = addPropertyMetadata(annotationMetadata, propertyMetadata)
                        }
                    }
                }
                anntationMetadata.put(parameterName, annotationMetadata)

                genericTypeMap.put(parameterName, resolveGenericTypes(param, boundTypes))
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

        private void visitConfigurationBuilder(ClassNode declaringClass,
                                               AnnotationMetadata annotationMetadata,
                                               ClassNode classNode,
                                               BeanDefinitionVisitor writer) {
            Boolean allowZeroArgs = annotationMetadata.getValue(ConfigurationBuilder.class, "allowZeroArgs", Boolean.class).orElse(false)
            List<String> prefixes = Arrays.asList(annotationMetadata.getValue(ConfigurationBuilder.class, "prefixes", String[].class).orElse(["set"] as String[]))
            String configurationPrefix = annotationMetadata.getValue(ConfigurationBuilder.class, String.class)
                    .map({ value -> value + "."}).orElse("")
            Set<String> includes = annotationMetadata.getValue(ConfigurationBuilder.class, "includes", Set.class).orElse(Collections.emptySet())
            Set<String> excludes = annotationMetadata.getValue(ConfigurationBuilder.class, "excludes", Set.class).orElse(Collections.emptySet())

            SourceUnit source = this.sourceUnit
            PublicMethodVisitor visitor = new PublicMethodVisitor(source) {
                @Override
                void accept(ClassNode cn, MethodNode method) {
                    String name = method.getName()
                    ClassNode returnType = method.getReturnType()
                    Parameter[] params = method.getParameters()
                    String prefix = getMethodPrefix(name)
                    String propertyName = NameUtils.decapitalize(name.substring(prefix.length()))
                    if (shouldExclude(includes, excludes, propertyName)) {
                        return
                    }

                    int paramCount = params.size()
                    if (paramCount < 2) {
                        Parameter paramType = params.size() == 1 ? params[0] : null
                        Object expectedType = paramType != null ? AstGenericUtils.resolveTypeReference(paramType.type) : null

                        PropertyMetadata metadata = configurationMetadataBuilder.visitProperty(
                                concreteClass,
                                declaringClass,
                                expectedType?.toString(),
                                configurationPrefix + propertyName,
                                null,
                                null
                        )

                        writer.visitConfigBuilderMethod(
                                prefix,
                                AstGenericUtils.resolveTypeReference(returnType),
                                name,
                                expectedType,
                                paramType != null ? resolveGenericTypes(paramType) : null,
                                metadata.path
                        )

                    } else if (paramCount == 2) {
                        // check the params are a long and a TimeUnit
                        Parameter first = params[0]
                        Parameter second = params[1]

                        PropertyMetadata metadata = configurationMetadataBuilder.visitProperty(
                                concreteClass,
                                declaringClass,
                                Duration.class.name,
                                configurationPrefix + propertyName,
                                null,
                                null
                        )

                        if (second.type.name == TimeUnit.class.name && first.type.name == "long") {
                            writer.visitConfigBuilderDurationMethod(
                                    prefix,
                                    AstGenericUtils.resolveTypeReference(returnType),
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

            visitor.accept(classNode)
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
}
