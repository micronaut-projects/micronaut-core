package org.particleframework.ast.groovy

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovyjarjarantlr.collections.AST
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.particleframework.aop.Around
import org.particleframework.aop.Introduction
import org.particleframework.aop.writer.AopProxyWriter
import org.particleframework.ast.groovy.annotation.AnnotationStereoTypeFinder
import org.particleframework.ast.groovy.utils.AstAnnotationUtils
import org.particleframework.ast.groovy.utils.AstGenericUtils
import org.particleframework.ast.groovy.utils.AstMessageUtils
import org.particleframework.ast.groovy.utils.PublicMethodVisitor
import org.particleframework.config.ConfigurationProperties
import org.particleframework.context.annotation.*
import org.particleframework.core.io.service.ServiceDescriptorGenerator
import org.particleframework.core.naming.NameUtils
import org.particleframework.core.util.ArrayUtil
import org.particleframework.inject.BeanConfiguration
import org.particleframework.inject.BeanDefinitionClass
import org.particleframework.inject.annotation.Executable
import org.particleframework.inject.writer.BeanConfigurationWriter
import org.particleframework.inject.writer.BeanDefinitionClassWriter
import org.particleframework.inject.writer.BeanDefinitionVisitor
import org.particleframework.inject.writer.BeanDefinitionWriter
import org.particleframework.inject.writer.ProxyingBeanDefinitionVisitor

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.*
import java.beans.Introspector
import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.ClassHelper.makeCached
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.getSetterName

/**
 * An AST transformation that produces metadata for use by the injection container
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class InjectTransform implements ASTTransformation, CompilationUnitAware {

    CompilationUnit unit
    AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        Map<AnnotatedNode, BeanDefinitionVisitor> beanDefinitionWriters = [:]

        List<ClassNode> classes = moduleNode.getClasses()
        if (classes.size() == 1) {
            ClassNode classNode = classes[0]
            if (classNode.nameWithoutPackage == 'package-info') {
                PackageNode packageNode = classNode.getPackage()
                AnnotationNode annotationNode = AstAnnotationUtils.findAnnotation(packageNode, Configuration.name)
                if (annotationNode != null) {
                    BeanConfigurationWriter writer = new BeanConfigurationWriter(classNode.packageName)
                    String configurationName = null

                    try {
                        writer.writeTo(source.configuration.targetDirectory)
                        configurationName = writer.getConfigurationClassName()
                    } catch (Throwable e) {
                        AstMessageUtils.error(source, classNode, "Error generating bean configuration for package-info class [${classNode.name}]: $e.message")
                    }

                    if(configurationName != null) {

                        try {
                            ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator()
                            File targetDirectory = source.configuration.targetDirectory
                            if (targetDirectory != null) {
                                generator.generate(targetDirectory, configurationName, BeanConfiguration.class)
                            }
                        } catch (Throwable e) {
                            AstMessageUtils.error(source, classNode, "Error generating bean configuration descriptor for package-info class [${classNode.name}]: $e.message")
                        }
                    }
                }

                return
            }
        }

        for (ClassNode classNode in classes) {
            if ((classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers()))) {
                continue
            }
            else if(classNode.isAbstract()) {
                if(stereoTypeFinder.hasStereoType(classNode, InjectVisitor.INTRODUCTION_TYPE)) {
                    InjectVisitor injectVisitor = new InjectVisitor(source, classNode)
                    injectVisitor.visitClass(classNode)
                    beanDefinitionWriters.putAll(injectVisitor.beanDefinitionWriters)
                }
            }
            else {

                InjectVisitor injectVisitor = new InjectVisitor(source, classNode)
                injectVisitor.visitClass(classNode)
                beanDefinitionWriters.putAll(injectVisitor.beanDefinitionWriters)
            }
        }



        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator()
        for (entry in beanDefinitionWriters) {
            BeanDefinitionVisitor beanDefWriter = entry.value
            File classesDir = source.configuration.targetDirectory
            String beanDefinitionClassName = null
            String beanTypeName = beanDefWriter.beanTypeName
            AnnotatedNode beanClassNode = entry.key
            try {
                String beanDefinitionName = beanDefWriter.beanDefinitionName
                BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter(beanTypeName, beanDefinitionName)
                beanClassWriter.setContextScope(stereoTypeFinder.hasStereoType(beanClassNode, Context.name))
                if(beanDefWriter instanceof ProxyingBeanDefinitionVisitor) {
                    String proxiedBeanDefinitionName = ((ProxyingBeanDefinitionVisitor) beanDefWriter).getProxiedBeanDefinitionName();
                    if(proxiedBeanDefinitionName != null) {
                        beanClassWriter.setReplaceBeanDefinitionName(
                                proxiedBeanDefinitionName
                        )
                    }
                }
                else {

                    AnnotationNode replacesAnn = AstAnnotationUtils.findAnnotation(beanClassNode, Replaces.class.name)
                    if(replacesAnn != null) {
                        beanClassWriter.setReplaceBeanName(((ClassExpression)replacesAnn.getMember("value")).type.name)
                    }
                }
                beanDefinitionClassName = beanClassWriter.getBeanDefinitionClassName()
                beanClassWriter.writeTo(classesDir)
            } catch (Throwable e) {
                AstMessageUtils.error(source, beanClassNode, "Error generating bean definition class for dependency injection of class [${beanTypeName}]: $e.message")
            }

            if (beanDefinitionClassName != null) {
                boolean abort = false
                try {
                    generator.generate(classesDir, beanDefinitionClassName, BeanDefinitionClass)
                }
                catch (Throwable e) {
                    abort = true
                    AstMessageUtils.error(source, beanClassNode, "Error generating bean definition class descriptor for dependency injection of class [${beanTypeName}]: $e.message")
                }
                if(!abort) {
                    try {
                        beanDefWriter.visitBeanDefinitionEnd()
                        beanDefWriter.writeTo(classesDir)
                    } catch (Throwable e) {
                        AstMessageUtils.error(source, beanClassNode, "Error generating bean definition for dependency injection of class [${beanTypeName}]: $e.message")
                    }
                }
            }

        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }

    private static class InjectVisitor extends ClassCodeVisitorSupport {
        public static final String AROUND_TYPE = "org.particleframework.aop.Around"
        public static final String INTRODUCTION_TYPE = "org.particleframework.aop.Introduction"
        final SourceUnit sourceUnit
        final ClassNode concreteClass
        final boolean isConfigurationProperties
        final boolean isFactoryClass
        final boolean isExecutableType
        final boolean isAopProxyType
        private final boolean isProxyTargetClass
        private final boolean isHotSwappable

        final Map<AnnotatedNode, BeanDefinitionVisitor> beanDefinitionWriters = [:]
        BeanDefinitionVisitor beanWriter
        static final AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()
        BeanDefinitionVisitor aopProxyWriter

        InjectVisitor(SourceUnit sourceUnit, ClassNode targetClassNode) {
            this(sourceUnit, targetClassNode, stereoTypeFinder.hasStereoType(targetClassNode, ConfigurationProperties))
        }

        InjectVisitor(SourceUnit sourceUnit, ClassNode targetClassNode, boolean isConfigurationProperties) {
            this.sourceUnit = sourceUnit
            this.concreteClass = targetClassNode
            this.isFactoryClass = stereoTypeFinder.hasStereoType(targetClassNode, Factory)
            this.isAopProxyType = stereoTypeFinder.hasStereoType(targetClassNode, AROUND_TYPE)
            this.isProxyTargetClass = isAopProxyType && stereoTypeFinder.isAttributeTrue(concreteClass, AROUND_TYPE, "proxyTarget")
            this.isHotSwappable = isProxyTargetClass && stereoTypeFinder.isAttributeTrue(concreteClass, AROUND_TYPE, "hotswap")

            this.isExecutableType = isAopProxyType || stereoTypeFinder.hasStereoType(targetClassNode, Executable)
            this.isConfigurationProperties = isConfigurationProperties
            if (isFactoryClass || isConfigurationProperties || stereoTypeFinder.hasStereoType(concreteClass, Scope) || stereoTypeFinder.hasStereoType(concreteClass, Bean)) {
                defineBeanDefinition(concreteClass)
            }
        }

        @Override
        void visitClass(ClassNode node) {
            if(stereoTypeFinder.hasStereoType(node, INTRODUCTION_TYPE)) {
                AnnotationNode scopeAnn = stereoTypeFinder.findAnnotationWithStereoType(node, Scope)
                AnnotationNode singletonAnn = stereoTypeFinder.findAnnotationWithStereoType(node, Singleton)

                String packageName= node.packageName
                String beanClassName = node.nameWithoutPackage

                AnnotationNode[] aroundMirrors = stereoTypeFinder
                                            .findAnnotationsWithStereoType(node, Around)
                AnnotationNode[] introductionMirrors = stereoTypeFinder
                        .findAnnotationsWithStereoType(node, Introduction)

                AnnotationNode[] annotationMirrors = ArrayUtil.concat(aroundMirrors, introductionMirrors)
                Object[] interceptorTypes = resolveTypeReferences(annotationMirrors)

                String scopeType = scopeAnn?.classNode?.name
                boolean isSingleton = singletonAnn != null
                boolean isInterface = node.isInterface()
                AopProxyWriter aopProxyWriter = new AopProxyWriter(
                        packageName,
                        beanClassName,
                        scopeType,
                        isInterface,
                        isSingleton,
                        interceptorTypes)
                populateProxyWriterConstructor(node, aopProxyWriter)
                beanDefinitionWriters.put(node, aopProxyWriter)
                visitIntroductionTypePublicMethods(aopProxyWriter, node)
            }
            else {

                ClassNode superClass = node.getSuperClass()
                List<ClassNode> superClasses = []
                while (superClass != null) {
                    superClasses.add(superClass)
                    superClass = superClass.getSuperClass()
                }
                superClasses = superClasses.reverse()
                for (classNode in superClasses) {
                    classNode.visitContents(this)
                }
                super.visitClass(node)
            }
        }

        protected void visitIntroductionTypePublicMethods(AopProxyWriter aopProxyWriter, ClassNode node) {
            PublicMethodVisitor publicMethodVisitor = new PublicMethodVisitor(sourceUnit) {

                @Override
                void accept(MethodNode methodNode) {
                    Map<String, Object> targetMethodParamsToType = [:]
                    Map<String, Object> targetMethodQualifierTypes = [:]
                    Map<String, Map<String, Object>> targetMethodGenericTypeMap = [:]
                    Object resolvedReturnType = AstGenericUtils.resolveTypeReference(methodNode.returnType)
                    Map<String,Object> resolvedGenericTypes = AstGenericUtils.buildGenericTypeInfo(methodNode.returnType)
                    populateParameterData(
                            methodNode.parameters,
                            targetMethodParamsToType,
                            targetMethodQualifierTypes,
                            targetMethodGenericTypeMap)


                    aopProxyWriter.visitAroundMethod(
                            AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                            resolvedReturnType,
                            resolvedGenericTypes,
                            methodNode.name,
                            targetMethodParamsToType,
                            targetMethodQualifierTypes,
                            targetMethodGenericTypeMap
                    )
                }

                @Override
                protected boolean isAcceptable(MethodNode methodNode
                ) {
                    return methodNode.isAbstract() && !methodNode.isFinal() && !methodNode.isStatic() && !methodNode.isSynthetic()
                }
            }
            publicMethodVisitor.accept(node)
        }


        @Override
        protected void visitConstructorOrMethod(MethodNode methodNode, boolean isConstructor) {
            String methodName = methodNode.name
            ClassNode declaringClass = methodNode.declaringClass
            if( isFactoryClass && !isConstructor && stereoTypeFinder.hasStereoType(methodNode, Bean)) {
                AnnotationNode beanAnn = stereoTypeFinder.findAnnotationWithStereoType(methodNode, Bean)
                ClassNode producedType = methodNode.returnType
                AnnotationNode scopeAnn = stereoTypeFinder.findAnnotationWithStereoType(methodNode, Scope.class)
                AnnotationNode singletonAnn = stereoTypeFinder.findAnnotationWithStereoType(methodNode, Singleton.class)
                String beanDefinitionPackage = concreteClass.packageName;
                String upperCaseMethodName = NameUtils.capitalize(methodNode.getName())
                String factoryMethodBeanDefinitionName =
                        beanDefinitionPackage + '.$' + concreteClass.nameWithoutPackage + '$' + upperCaseMethodName + "Definition"

                BeanDefinitionWriter beanMethodWriter = new BeanDefinitionWriter(
                        producedType.packageName,
                        producedType.nameWithoutPackage,
                        factoryMethodBeanDefinitionName,
                        producedType.name,
                        producedType.isInterface(),
                        scopeAnn?.classNode?.name,
                        singletonAnn != null)

                Map<String, Object> paramsToType = [:]
                Map<String, Object> qualifierTypes = [:]
                Map<String, Map<String,Object>> genericTypeMap = [:]
                populateParameterData(methodNode.parameters, paramsToType, qualifierTypes, genericTypeMap)

                beanMethodWriter.visitBeanFactoryMethod(AstGenericUtils.resolveTypeReference(concreteClass), methodName, paramsToType, qualifierTypes, genericTypeMap)
                String beanMethodDeclaringType = declaringClass.name
                beanMethodWriter.visitMethodAnnotationSource(
                        beanMethodDeclaringType,
                        methodName,
                        paramsToType
                )

                if(stereoTypeFinder.hasStereoType(methodNode, AROUND_TYPE)) {
                    AnnotationNode[] annotations = stereoTypeFinder.findAnnotationsWithStereoType(methodNode, Around)
                    Object[] interceptorTypeReferences = resolveTypeReferences(annotations)
                    AopProxyWriter proxyWriter = new AopProxyWriter(
                            beanMethodWriter,
                            true,
                            false,
                            interceptorTypeReferences)
                    if(producedType.isInterface()) {
                        proxyWriter.visitBeanDefinitionConstructor()
                    }
                    else {
                        populateProxyWriterConstructor(producedType, proxyWriter)
                    }
                    proxyWriter.visitMethodAnnotationSource(
                            beanMethodDeclaringType,
                            methodName,
                            paramsToType
                    )

                    new PublicMethodVisitor(sourceUnit) {


                        @Override
                        void accept(MethodNode targetBeanMethodNode) {
                            Map<String, Object> targetMethodParamsToType = [:]
                            Map<String, Object> targetMethodQualifierTypes = [:]
                            Map<String, Map<String, Object>> targetMethodGenericTypeMap = [:]
                            Object resolvedReturnType = AstGenericUtils.resolveTypeReference(targetBeanMethodNode.returnType)
                            Map<String, Object> returnTypeGenerics = AstGenericUtils.buildGenericTypeInfo(targetBeanMethodNode.returnType)
                            populateParameterData(
                                    targetBeanMethodNode.parameters,
                                    targetMethodParamsToType,
                                    targetMethodQualifierTypes,
                                    targetMethodGenericTypeMap )
                            beanMethodWriter.visitExecutableMethod(
                                    AstGenericUtils.resolveTypeReference(targetBeanMethodNode.declaringClass),
                                    resolvedReturnType,
                                    returnTypeGenerics,
                                    targetBeanMethodNode.name,
                                    targetMethodParamsToType,
                                    targetMethodQualifierTypes,
                                    targetMethodGenericTypeMap
                            ).visitMethodAnnotationSource(
                                    beanMethodDeclaringType,
                                    methodName,
                                    paramsToType
                            ).visitEnd()


                            proxyWriter.visitAroundMethod(
                                    AstGenericUtils.resolveTypeReference(targetBeanMethodNode.declaringClass),
                                    resolvedReturnType,
                                    returnTypeGenerics,
                                    targetBeanMethodNode.name,
                                    targetMethodParamsToType,
                                    targetMethodQualifierTypes,
                                    targetMethodGenericTypeMap
                            );
                        }
                    }.accept(methodNode.getReturnType())
                    beanDefinitionWriters.put(new AnnotatedNode(), proxyWriter)

                }
                Expression preDestroy = beanAnn.getMember("preDestroy")
                if(preDestroy != null) {
                    String destroyMethodName = preDestroy.text
                    MethodNode destroyMethod = producedType.getMethod(destroyMethodName)
                    if(destroyMethod != null) {
                        beanMethodWriter.visitPreDestroyMethod(destroyMethod.declaringClass.name, destroyMethodName)
                    }
                }
                beanDefinitionWriters.put(methodNode, beanMethodWriter)
            }
            else if (stereoTypeFinder.hasStereoType(methodNode, Inject.name, PostConstruct.name, PreDestroy.name)) {
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
                        boolean overriddenInjected = overridden && stereoTypeFinder.hasStereoType(overriddenMethod, Inject)

                        if (isParent && isPackagePrivate && !isPackagePrivateAndPackagesDiffer && overriddenInjected) {
                            // bail out if the method has been overridden by another method annotated with @INject
                            return
                        }
                        if(isParent && overridden && !overriddenInjected && !isPackagePrivateAndPackagesDiffer && !isPrivate) {
                            // bail out if the overridden method is package private and in the same package
                            // and is not annotated with @Inject
                            return
                        }
                        if (!requiresReflection && isInheritedAndNotPublic(methodNode, declaringClass, methodNode.modifiers)) {
                            requiresReflection = true
                        }

                        Map<String, Object> paramsToType = [:]
                        Map<String, Object> qualifierTypes = [:]
                        Map<String, Map<String, Object>> genericTypeMap = [:]
                        populateParameterData(methodNode.parameters, paramsToType, qualifierTypes, genericTypeMap)

                        if (stereoTypeFinder.hasStereoType(methodNode, PostConstruct.name)) {
                            beanWriter.visitPostConstructMethod(
                                    AstGenericUtils.resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodName,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        } else if (stereoTypeFinder.hasStereoType(methodNode, PreDestroy.name)) {
                            beanWriter.visitPreDestroyMethod(
                                    AstGenericUtils.resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodName,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        } else {
                            beanWriter.visitMethodInjectionPoint(
                                    AstGenericUtils.resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                    methodName,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        }


                    }
                }
            }
            else if(!isConstructor) {
                boolean isPublic = methodNode.isPublic() && !methodNode.isStatic() && !methodNode.isAbstract()
                if((isExecutableType && isPublic) || stereoTypeFinder.hasStereoType(methodNode, Executable.name)) {
                    if(declaringClass != ClassHelper.OBJECT_TYPE) {

                        defineBeanDefinition(concreteClass)
                        Map<String, Object> returnTypeGenerics = AstGenericUtils.buildGenericTypeInfo(methodNode.returnType)

                        Map<String, Object> paramsToType = [:]
                        Map<String, Object> qualifierTypes = [:]
                        Map<String, Map<String, Object>> genericTypeMap = [:]
                        populateParameterData(methodNode.parameters, paramsToType, qualifierTypes, genericTypeMap)

                        beanWriter.visitExecutableMethod(
                                AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                                AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                returnTypeGenerics,
                                methodName,
                                paramsToType,
                                qualifierTypes,
                                genericTypeMap)
                                .visitEnd()

                        if((isAopProxyType && isPublic) || stereoTypeFinder.hasStereoType(methodNode, AROUND_TYPE)) {

                            AnnotationNode[] annotations = stereoTypeFinder.findAnnotationsWithStereoType(methodNode, Around)
                            Object[] interceptorTypeReferences = resolveTypeReferences(annotations)
                            boolean isProxyClass = stereoTypeFinder.isAttributeTrue(methodNode, AROUND_TYPE, "proxyTarget")
                            boolean isHotSwap = isProxyTargetClass && stereoTypeFinder.isAttributeTrue(methodNode, AROUND_TYPE, "hotswap");
                            AopProxyWriter proxyWriter = resolveProxyWriter(
                                    isProxyClass,
                                    isHotSwap,
                                    false,
                                    interceptorTypeReferences
                            )


                            if(proxyWriter != null) {

                                proxyWriter.visitInterceptorTypes(interceptorTypeReferences)
                                proxyWriter.visitAroundMethod(
                                        AstGenericUtils.resolveTypeReference(methodNode.declaringClass),
                                        AstGenericUtils.resolveTypeReference(methodNode.returnType),
                                        returnTypeGenerics,
                                        methodName,
                                        paramsToType,
                                        qualifierTypes,
                                        genericTypeMap
                                )
                            }
                        }
                    }
                }
            }

        }

        private AopProxyWriter resolveProxyWriter(
                boolean isProxyTargetClass,
                boolean isHotSwappable,
                boolean isFactoryType,
                Object[] interceptorTypeReferences) {
            AopProxyWriter proxyWriter = (AopProxyWriter) aopProxyWriter
            if (proxyWriter == null) {

                proxyWriter = new AopProxyWriter(beanWriter,isProxyTargetClass, isHotSwappable, interceptorTypeReferences)


                ClassNode targetClass = concreteClass
                populateProxyWriterConstructor(targetClass, proxyWriter)
                String beanDefinitionName = beanWriter.getBeanDefinitionName()
                if(isFactoryType) {
                    proxyWriter
                            .visitSuperFactoryType(beanDefinitionName)
                }
                else {
                    proxyWriter
                            .visitSuperType(beanDefinitionName)
                }


                this.aopProxyWriter = proxyWriter

                def node = new AnnotatedNode()
                def replaces = new AnnotationNode(makeCached(Replaces))
                replaces.setMember('value', classX(targetClass.plainNodeReference))
                node.addAnnotation(replaces)
                beanDefinitionWriters.put(node, proxyWriter)
            }
            proxyWriter
        }

        protected void populateProxyWriterConstructor(ClassNode targetClass, AopProxyWriter proxyWriter) {
            List<ConstructorNode> constructors = targetClass.getDeclaredConstructors()
            if (constructors.isEmpty()) {
                proxyWriter.visitBeanDefinitionConstructor()
            } else {
                ConstructorNode constructorNode = findConcreteConstructor(constructors)

                if (constructorNode != null) {
                    Map<String, Object> constructorParamsToType = [:]
                    Map<String, Object> constructorQualifierTypes = [:]
                    Map<String, Map<String, Object>> constructorGenericTypeMap = [:]
                    Parameter[] parameters = constructorNode.parameters
                    populateParameterData(parameters,
                            constructorParamsToType,
                            constructorQualifierTypes,
                            constructorGenericTypeMap)
                    proxyWriter.visitBeanDefinitionConstructor(constructorParamsToType, constructorQualifierTypes, constructorGenericTypeMap)


                } else {
                    addError("Class must have at least one public constructor in order to be a candidate for dependency injection", targetClass)
                }

            }
        }

        protected Object[] resolveTypeReferences(AnnotationNode[] annotationNodes) {
            return annotationNodes.collect() { AnnotationNode node -> AstGenericUtils.resolveTypeReference(node.classNode) } as Object[]
        }

        protected List<Object> resolveGenericTypes(ClassNode type) {
            List<Object> generics = []
            for(gt in type.genericsTypes) {
                if(!gt.isPlaceholder()) {
                    generics.add(AstGenericUtils.resolveTypeReference(gt.type))
                }
                else if(gt.isWildcard()) {
                    ClassNode[] upperBounds = gt.upperBounds
                    if(upperBounds != null && upperBounds.length == 1) {
                        generics.add(AstGenericUtils.resolveTypeReference(upperBounds[0]))
                    }
                }
            }
            return generics
        }

        protected boolean isPackagePrivate(AnnotatedNode annotatedNode, int modifiers) {
            return ((!Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers) && !Modifier.isPrivate(modifiers)) || !annotatedNode.getAnnotations(makeCached(PackageScope)).isEmpty())
        }


        @Override
        void visitField(FieldNode fieldNode) {
            if(fieldNode.name == 'metaClass') return
            ClassNode declaringClass = fieldNode.declaringClass
            boolean isInject = stereoTypeFinder.hasStereoType(fieldNode, Inject)
            boolean isValue = !isInject && (stereoTypeFinder.hasStereoType(fieldNode, Value) || isConfigurationProperties)

            if ((isInject || isValue) && declaringClass.getProperty(fieldNode.getName()) == null) {
                defineBeanDefinition(concreteClass)
                if (!fieldNode.isStatic()) {
                    Object qualifierRef = resolveQualifier(fieldNode)


                    boolean isPrivate = Modifier.isPrivate(fieldNode.getModifiers())
                    boolean requiresReflection = isPrivate || isInheritedAndNotPublic(fieldNode, fieldNode.declaringClass, fieldNode.modifiers)
                    if(!beanWriter.isValidated()) {
                        if(stereoTypeFinder.hasStereoType(fieldNode, "javax.validation.Constraint")) {
                            beanWriter.setValidated(true)
                        }
                    }
                    if(isValue) {
                        beanWriter.visitFieldValue(
                                declaringClass.isResolved() ? declaringClass.typeClass : declaringClass.name, qualifierRef,
                                requiresReflection,
                                fieldNode.type.isResolved() ? fieldNode.type.typeClass : fieldNode.type.name,
                                fieldNode.name,
                                isConfigurationProperties
                        )
                    }
                    else {
                        beanWriter.visitFieldInjectionPoint(
                                declaringClass.isResolved() ? declaringClass.typeClass : declaringClass.name, qualifierRef,
                                requiresReflection,
                                fieldNode.type.isResolved() ? fieldNode.type.typeClass : fieldNode.type.name,
                                fieldNode.name
                        )
                    }
                }
            }
        }

        Object resolveQualifier(AnnotatedNode annotatedNode) {
            AnnotationNode qualifierAnn = stereoTypeFinder.findAnnotationWithStereoType(annotatedNode, Qualifier)
            ClassNode qualifierClassNode = qualifierAnn?.classNode
            Object qualifierRef = qualifierClassNode?.isResolved() ? qualifierClassNode.typeClass : qualifierClassNode?.name
            return qualifierRef
        }

        @Override
        void visitProperty(PropertyNode propertyNode) {
            FieldNode fieldNode = propertyNode.field
            if(fieldNode.name == 'metaClass') return
            boolean isInject = fieldNode != null && stereoTypeFinder.hasStereoType(fieldNode, Inject)
            boolean isValue = !isInject && fieldNode != null && (stereoTypeFinder.hasStereoType(fieldNode, Value) || isConfigurationProperties)
            if (!propertyNode.isStatic() && (isInject || isValue)) {
                defineBeanDefinition(concreteClass)
                Object qualifier = resolveQualifier(fieldNode)

                ClassNode fieldType = fieldNode.type

                GenericsType[] genericsTypes = fieldType.genericsTypes
                Map<String, Object> genericTypeList = null
                if (genericsTypes != null && genericsTypes.length > 0) {
                    genericTypeList = AstGenericUtils.buildGenericTypeInfo(fieldType)
                } else if (fieldType.isArray()) {
                    genericTypeList = [:]
                    genericTypeList.put(fieldNode.name, AstGenericUtils.resolveTypeReference(fieldType.componentType))
                }
                ClassNode declaringClass = fieldNode.declaringClass
                if(!beanWriter.isValidated()) {
                    if(stereoTypeFinder.hasStereoType(fieldNode, "javax.validation.Constraint")) {
                        beanWriter.setValidated(true)
                    }
                }

                if(isInject) {

                    beanWriter.visitSetterInjectionPoint(
                            AstGenericUtils.resolveTypeReference(declaringClass),
                            qualifier,
                            false,
                            AstGenericUtils.resolveTypeReference(fieldType),
                            fieldNode.name,
                            getSetterName(propertyNode.name),
                            genericTypeList
                    )
                }
                else if(isValue){
                    beanWriter.visitSetterValue(
                            AstGenericUtils.resolveTypeReference(declaringClass),
                            qualifier,
                            false,
                            AstGenericUtils.resolveTypeReference(fieldType),
                            fieldNode.name,
                            getSetterName(propertyNode.name),
                            genericTypeList,
                            isConfigurationProperties
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
            if (!beanDefinitionWriters.containsKey(classNode) && !classNode.isAbstract()) {
                ClassNode providerGenericType = AstGenericUtils.resolveInterfaceGenericType(classNode, Provider)
                boolean isProvider = providerGenericType != null
                AnnotationStereoTypeFinder annotationStereoTypeFinder = new AnnotationStereoTypeFinder()
                AnnotationNode scopeAnn = annotationStereoTypeFinder.findAnnotationWithStereoType(classNode, Scope.class)
                AnnotationNode singletonAnn = annotationStereoTypeFinder.findAnnotationWithStereoType(classNode, Singleton.class)

                if (isProvider) {
                    beanWriter = new BeanDefinitionWriter(
                            classNode.packageName,
                            classNode.nameWithoutPackage,
                            providerGenericType.name,
                            classNode.isInterface(),
                            scopeAnn?.classNode?.name,
                            singletonAnn != null)
                } else {

                    beanWriter = new BeanDefinitionWriter(
                            classNode.packageName,
                            classNode.nameWithoutPackage,
                            scopeAnn?.classNode?.name,
                            singletonAnn != null)
                }
                beanDefinitionWriters.put(classNode, beanWriter)



                List<ConstructorNode> constructors = classNode.getDeclaredConstructors()

                if (constructors.isEmpty()) {
                    beanWriter.visitBeanDefinitionConstructor(Collections.emptyMap(), null, null)

                } else {
                    ConstructorNode constructorNode = findConcreteConstructor(constructors)
                    if (constructorNode != null) {
                        Map<String, Object> paramsToType = [:]
                        Map<String, Object> qualifierTypes = [:]
                        Map<String, Map<String, Object>> genericTypeMap = [:]
                        Parameter[] parameters = constructorNode.parameters
                        populateParameterData(parameters, paramsToType, qualifierTypes, genericTypeMap)
                        beanWriter.visitBeanDefinitionConstructor(paramsToType, qualifierTypes, genericTypeMap)
                    } else {
                        addError("Class must have at least one public constructor in order to be a candidate for dependency injection", classNode)
                    }
                }

                if(isConfigurationProperties) {
                    SourceUnit su = sourceUnit
                    classNode.getInnerClasses().each { InnerClassNode inner->
                        if(Modifier.isStatic(inner.getModifiers()) && Modifier.isPublic(inner.getModifiers()) && inner.getDeclaredConstructors().size() == 0) {
                            def innerAnnotation = new AnnotationNode(make(ConfigurationProperties))
                            String innerClassName = inner.getNameWithoutPackage() - classNode.getNameWithoutPackage()
                            innerClassName = innerClassName.substring(1) // remove starting dollar
                            String newPath = Introspector.decapitalize(innerClassName)
                            innerAnnotation.setMember("value", constX(newPath))
                            inner.addAnnotation(innerAnnotation)
                            new InjectVisitor(su, inner,true).visitClass(inner)
                        }
                    }
                }


                if(isAopProxyType) {
                    AnnotationNode[] annotations = stereoTypeFinder.findAnnotationsWithStereoType(classNode, Around)
                    Object[] interceptorTypeReferences = resolveTypeReferences(annotations)
                    resolveProxyWriter(isHotSwappable, isProxyTargetClass, false, interceptorTypeReferences)
                }

            } else if (!classNode.isAbstract()) {
                beanWriter = beanDefinitionWriters.get(classNode)
            }
        }

        private ConstructorNode findConcreteConstructor(List<ConstructorNode> constructors) {
            List<ConstructorNode> publicConstructors = findPublicConstructors(constructors)

            ConstructorNode constructorNode
            if (publicConstructors.size() == 1) {
                constructorNode = publicConstructors[0]
            } else {
                constructorNode = publicConstructors.find() { it.getAnnotations(makeCached(Inject)) }
            }
            constructorNode
        }

        private void populateParameterData(Parameter[] parameters, Map<String, Object> paramsToType, Map<String, Object> qualifierTypes, Map<String, Map<String, Object>> genericTypeMap) {
            for (param in parameters) {
                ClassNode parameterType = param.type
                String parameterName = param.name
                if (parameterType.isResolved()) {
                    paramsToType.put(parameterName, parameterType.typeClass)
                } else {
                    paramsToType.put(parameterName, parameterType.name)
                }

                Object qualifier = resolveQualifier(param)
                if (qualifier != null) {
                    qualifierTypes.put(parameterName, qualifier)
                }

                GenericsType[] genericsTypes = parameterType.genericsTypes
                if (genericsTypes != null && genericsTypes.length > 0) {
                    Map<String, Object> resolvedGenericTypes = AstGenericUtils.extractPlaceholders(parameterType)
                    genericTypeMap.put(parameterName, resolvedGenericTypes)
                } else if (parameterType.isArray()) {
                    Map<String, Object> genericTypeList = [:]
                    genericTypeList.put('E', AstGenericUtils.resolveTypeReference(parameterType.componentType))
                    genericTypeMap.put(parameterName, genericTypeList)
                }
            }
        }


        private List<ConstructorNode> findPublicConstructors(List<ConstructorNode> constructorNodes) {
            List<ConstructorNode> publicConstructors = []
            for (node in constructorNodes) {
                if (Modifier.isPublic(node.modifiers)) {
                    publicConstructors.add(node)
                }
            }
            return publicConstructors
        }
    }

}
