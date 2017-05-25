package org.particleframework.ast.groovy

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.particleframework.ast.groovy.annotation.AnnotationStereoTypeFinder
import org.particleframework.ast.groovy.descriptor.ServiceDescriptorGenerator
import org.particleframework.ast.groovy.utils.AstAnnotationUtils
import org.particleframework.ast.groovy.utils.AstClassUtils
import org.particleframework.ast.groovy.utils.AstGenericUtils
import org.particleframework.ast.groovy.utils.AstMessageUtils
import org.particleframework.context.BeanResolutionContext
import org.particleframework.context.BeanContext
import org.particleframework.context.AbstractBeanDefinition
import org.particleframework.context.DefaultBeanResolutionContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.annotation.Configuration
import org.particleframework.context.annotation.Context
import org.particleframework.inject.BeanConfiguration
import org.particleframework.inject.BeanDefinition
import org.particleframework.inject.BeanDefinitionClass
import org.particleframework.inject.BeanFactory
import org.particleframework.inject.DisposableBeanDefinition
import org.particleframework.inject.InitializingBeanDefinition
import org.particleframework.inject.InjectableBeanDefinition
import org.particleframework.inject.asm.BeanDefinitionClassWriter
import org.particleframework.inject.asm.BeanDefinitionWriter
import org.particleframework.inject.asm.BeanConfigurationWriter

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Scope
import javax.inject.Singleton
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.ClassHelper.makeCached
import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * An AST transformation that produces metadata for use by the injection container
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class InjectTransform implements ASTTransformation, CompilationUnitAware {

    private static final String INSTANCE_VAR_NAME = '$instance'
    private static final ClassNode DEFAULT_COMPONENT_DEFINITION = makeCached(AbstractBeanDefinition)

    CompilationUnit unit
    AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        Map<ClassNode, ClassNode> componentDefinitionClassNodes = [:]
        Map<ClassNode, BeanDefinitionWriter> beanDefinitionWriters = [:]

        List<ClassNode> classes = moduleNode.getClasses()
        if (classes.size() == 1) {
            ClassNode classNode = classes[0]
            if (classNode.nameWithoutPackage == 'package-info') {
                PackageNode packageNode = classNode.getPackage()
                AnnotationNode annotationNode = AstAnnotationUtils.findAnnotation(packageNode, Configuration.name)
                if (annotationNode != null) {
                    try {
                        BeanConfigurationWriter writer = new BeanConfigurationWriter()
                        String configurationName = writer.writeConfiguration(classNode.packageName, source.configuration.targetDirectory)
                        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator()
                        File targetDirectory = source.configuration.targetDirectory
                        if (targetDirectory != null) {
                            generator.generate(targetDirectory, configurationName, BeanConfiguration.class)
                        }
                    } catch (Throwable e) {
                        e.printStackTrace()
                        AstMessageUtils.error(source, classNode, "Error generating bean configuration for package-info class [${classNode.name}]: $e.message")
                    }
                }

                return
            }
        }

        for (ClassNode classNode in classes) {
            if (classNode.isAbstract() || (classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers()))) {
                continue
            }
            InjectVisitor injectVisitor = new InjectVisitor(source, classNode)
            injectVisitor.visitClass(classNode)
            componentDefinitionClassNodes.putAll(injectVisitor.beanDefinitionClassNodes)
            beanDefinitionWriters.putAll(injectVisitor.beanDefinitionWriters)
        }

        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator()
        for (entry in beanDefinitionWriters) {
            BeanDefinitionWriter beanDefWriter = entry.value
            File classesDir = source.configuration.targetDirectory

            try {
                BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter()
                String beanDefinitionClassName = beanClassWriter.writeBeanDefinitionClass(classesDir, beanDefWriter.beanDefinitionName, stereoTypeFinder.hasStereoType(entry.key, Context.name))
                beanDefWriter.visitBeanDefinitionEnd(classesDir)
                generator.generate(classesDir, beanDefinitionClassName, BeanDefinitionClass)
            } catch (Throwable e) {
                e.printStackTrace()
                AstMessageUtils.error(source, entry.key, "Error generating bean definition for dependency injection of class [${entry.key.name}]: $e.message")
            }
        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }

    private static class InjectVisitor extends ClassCodeVisitorSupport {
        final SourceUnit sourceUnit
        final Map<ClassNode, ClassNode> beanDefinitionClassNodes = [:]
        final ClassNode concreteClass
        final Map<ClassNode, BeanDefinitionWriter> beanDefinitionWriters = [:]
        BeanDefinitionWriter beanWriter

        boolean isProvider = false
        ClassNode currentBeanDef = null
        BlockStatement currentConstructorBody = null
        BlockStatement currentBuildBody = null
        BlockStatement currentInjectBody = null
        BlockStatement currentStartupBody = null
        BlockStatement currentShutdownBody = null
        Parameter currentContextParam = null
        int methodIndex = 0
        int fieldIndex = 0
        Parameter currentResolutionContextParam = null
        VariableExpression currentBuildInstance = null
        VariableExpression currentInjectInstance = null
        AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()

        InjectVisitor(SourceUnit sourceUnit, ClassNode targetClassNode) {
            this.sourceUnit = sourceUnit
            this.concreteClass = targetClassNode
            if (stereoTypeFinder.hasStereoType(concreteClass, Scope)) {
                defineBeanDefinition(concreteClass)
            }
        }

        @Override
        void visitClass(ClassNode node) {

            super.visitClass(node)
            ClassNode superClass = node.getSuperClass()
            while (superClass != null) {
                superClass.visitContents(this)
                superClass = superClass.getSuperClass()
            }
            if (node.isAbstract()) return

            if (currentBuildBody != null && !currentBeanDef.getNodeMetaData("injected")) {
                def thisX = varX("this")
                // set the meta class to null to save memory
                def injectBeanArgs = args(varX(currentResolutionContextParam), varX(currentContextParam), currentInjectInstance)
                currentBuildBody.addStatement(
                        stmt(callX(thisX, "injectBean", injectBeanArgs))
                )
                if (currentStartupBody != null) {
                    makeBeanDefinitionInitializable()
                    currentBuildBody.addStatement(
                            stmt(callX(thisX, "postConstruct", injectBeanArgs))
                    )
                }
                if (currentShutdownBody != null) {
                    makeBeanDefinitionDisposable()
                }
                if (isProvider) {
                    currentBuildBody.addStatement(
                            returnS(
                                    callThisX("injectAnother", args(
                                            varX(currentResolutionContextParam),
                                            varX(currentContextParam),
                                            callX(currentBuildInstance, "get")
                                    ))

                            )
                    )
                }
                currentInjectBody.addStatement(
                        returnS(currentInjectInstance)
                )

                currentBeanDef.putNodeMetaData("injected", Boolean.TRUE)
            }
        }

        private void makeBeanDefinitionDisposable() {
            makeLifeCycleHook(DisposableBeanDefinition, 'dispose', "preDestroy", currentShutdownBody)
        }

        private void makeBeanDefinitionInitializable() {
            makeLifeCycleHook(InitializingBeanDefinition, 'initialize', "postConstruct", currentStartupBody)
        }

        private void makeLifeCycleHook(Class interfaceType, String interfaceMethod, String protectedDelegateMethod, BlockStatement body) {
            ClassNode targetClass = concreteClass.plainNodeReference
            currentBeanDef.addInterface(
                    GenericsUtils.makeClassSafeWithGenerics(interfaceType, concreteClass)
            )

            // implement the initialize method such that
            // T initialize(BeanContext $context, T $instance) {
            //    return postConstruct(new DefaultBeanResolutionContext($context, this), $context, $instance)
            // }
            Parameter[] interfaceMethodParams = params(
                    param(makeCached(BeanContext), '$context'),
                    param(targetClass, INSTANCE_VAR_NAME)
            )
            ClassNode resolutionContext = makeCached(DefaultBeanResolutionContext)
            def newResolutionContext = ctorX(resolutionContext, args(
                    varX(interfaceMethodParams[0]),
                    varX("this")
            ))
            ArgumentListExpression interfaceMethodArgs = paramsToArguments(interfaceMethodParams)
            interfaceMethodArgs.expressions.add(0, newResolutionContext)
            currentBeanDef.addMethod(
                    interfaceMethod,
                    Modifier.PUBLIC,
                    targetClass,
                    interfaceMethodParams,
                    null,
                    returnS(
                            callThisX(protectedDelegateMethod, interfaceMethodArgs)
                    )
            )

            // override the postConstruct method to call post construct hooks
            Parameter[] protectedDelegateMethodParams = params(
                    param(makeCached(BeanResolutionContext), '$resolutionContext'),
                    param(makeCached(BeanContext), '$context'),
                    param(targetClass, INSTANCE_VAR_NAME)
            )

            currentBeanDef.addMethod(
                    protectedDelegateMethod,
                    Modifier.PROTECTED,
                    ClassHelper.OBJECT_TYPE,
                    protectedDelegateMethodParams,
                    null,
                    block(
                            body,
                            stmt(callSuperX(protectedDelegateMethod, paramsToArguments(protectedDelegateMethodParams)))
                    )
            )
        }

        @Override
        protected void visitConstructorOrMethod(MethodNode methodNode, boolean isConstructor) {
            if (stereoTypeFinder.hasStereoType(methodNode, Inject.name, PostConstruct.name, PreDestroy.name)) {
                ClassNode classNode = methodNode.declaringClass
                defineBeanDefinition(concreteClass)
                boolean isParent = classNode != concreteClass

                if (!isConstructor && currentConstructorBody != null) {
                    if (!methodNode.isStatic()) {
                        boolean isPackagePrivate = isPackagePrivate(methodNode)
                        boolean isPrivate = methodNode.isPrivate()
                        MethodNode overriddenMethod = isParent ? concreteClass.getMethod(methodNode.name, methodNode.parameters) : methodNode
                        boolean overridden = isParent && overriddenMethod.declaringClass != methodNode.declaringClass

                        if (isParent && !isPrivate && !isPackagePrivate) {

                            if (overridden) {
                                // bail out if the method has been overridden, since it will have already been handled
                                return
                            }
                        }
                        Expression methodName = constX(methodNode.name)

                        boolean isPackagePrivateAndPackagesDiffer = overridden && (overriddenMethod.declaringClass.packageName != methodNode.declaringClass.packageName) && isPackagePrivate
                        boolean requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer
                        boolean overriddenInjected = overridden && stereoTypeFinder.hasStereoType(overriddenMethod, Inject)

                        if (isParent && isPackagePrivate && !isPackagePrivateAndPackagesDiffer && !overriddenInjected) {
                            // bail out if the overridden method is package private and in the same package
                            // and is not annotated with @Inject
                            return
                        }
                        if (!requiresReflection && isInheritedAndNotPublic(methodNode, methodNode.declaringClass, methodNode.modifiers)) {
                            requiresReflection = true
                        }

                        Map<String, Object> paramsToType = [:]
                        Map<String, Object> qualifierTypes = [:]
                        Map<String, List<Object>> genericTypeMap = [:]
                        populateParameterData(methodNode.parameters, paramsToType, qualifierTypes, genericTypeMap)
                        ClassNode declaringClass = methodNode.declaringClass

                        if(stereoTypeFinder.hasStereoType(methodNode, PostConstruct.name)) {
                            beanWriter.visitPostConstructMethod(
                                    resolveTypeReference(declaringClass),
                                    methodNode.modifiers,
                                    resolveTypeReference(methodNode.returnType),
                                    methodNode.name,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        }
                        else if(stereoTypeFinder.hasStereoType(methodNode, PreDestroy.name)) {
                            beanWriter.visitPreDestroyMethod(
                                    resolveTypeReference(declaringClass),
                                    methodNode.modifiers,
                                    resolveTypeReference(methodNode.returnType),
                                    methodNode.name,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        }
                        else {
                            beanWriter.visitMethodInjectionPoint(
                                    resolveTypeReference(declaringClass),
                                    methodNode.modifiers,
                                    resolveTypeReference(methodNode.returnType),
                                    methodNode.name,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        }


                    }
                }
            }

        }

        boolean isPackagePrivate(MethodNode methodNode) {
            return ((!methodNode.isProtected() && !methodNode.isPublic() && !methodNode.isPrivate()) || !methodNode.getAnnotations(makeCached(PackageScope)).isEmpty())
        }

        @Override
        void visitField(FieldNode fieldNode) {
            ClassNode declaringClass = fieldNode.declaringClass
            if (stereoTypeFinder.hasStereoType(fieldNode, Inject) && declaringClass.getProperty(fieldNode.getName()) == null) {
                defineBeanDefinition(concreteClass)
                if (currentConstructorBody != null) {
                    if (!fieldNode.isStatic()) {

                        int modifiers = fieldNode.getModifiers()
                        boolean requiresReflection = Modifier.isPrivate(modifiers)
                        if (currentInjectInstance != null && !requiresReflection) {
                            if (isInheritedAndNotPublic(fieldNode, declaringClass, modifiers)) {
                                requiresReflection = true
                            } else {
                                boolean isProvider = AstClassUtils.implementsInterface(fieldNode.type, Provider) && fieldNode.type.genericsTypes
                                ArgumentListExpression lookupArgs = args(varX(currentResolutionContextParam), varX(currentContextParam))

                                if (isProvider) {
                                    lookupArgs.addExpression(classX(fieldNode.type.genericsTypes[0].type.plainNodeReference))
                                }
                                lookupArgs.addExpression(constX(fieldIndex))

                                String methodName = "getBeanForField"
                                if (isProvider) {
                                    methodName = "getBeanProviderForField"
                                }
                                Expression injectCall = assignX(propX(currentInjectInstance, fieldNode.name), callThisX(methodName, lookupArgs))
                                currentInjectBody.addStatement(
                                        stmt(injectCall)
                                )
                            }
                        }

                        AnnotationNode qualifierAnn = stereoTypeFinder.findAnnotationWithStereoType(fieldNode, Qualifier)
                        ClassNode qualifierClassNode = qualifierAnn?.classNode
                        Object qualifierRef = qualifierClassNode?.isResolved() ? qualifierClassNode.typeClass : qualifierClassNode?.name
                        beanWriter.visitFieldInjectionPoint(
                                declaringClass.isResolved() ? declaringClass.typeClass : declaringClass.name, qualifierRef,
                                fieldNode.modifiers,
                                fieldNode.type.isResolved() ? fieldNode.type.typeClass : fieldNode.type.name,
                                fieldNode.name
                        )

                        VariableExpression fieldVar = declareFieldVar('$field' + fieldIndex++, fieldNode)
                        ArgumentListExpression methodArgs = args(fieldVar)
                        AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()

                        if (qualifierAnn != null) {
                            methodArgs.addExpression(callX(fieldVar, "getAnnotation", classX(qualifierAnn.classNode)))
                        }
                        methodArgs.addExpression(constX(requiresReflection))
                        MethodCallExpression addInjectionPointMethod = callX(varX("this"), "addInjectionPoint", methodArgs)
                        currentConstructorBody.addStatement(
                                stmt(addInjectionPointMethod)
                        )
                    }
                }
            }
        }

        protected VariableExpression declareFieldVar(String name, FieldNode fieldNode) {
            MethodCallExpression getFieldCall = callX(
                    classX(fieldNode.declaringClass.plainNodeReference),
                    "getDeclaredField",
                    args(constX(fieldNode.name)))

            def fieldVar = varX(name, makeCached(Field))
            currentConstructorBody.addStatement(
                    declS(fieldVar, getFieldCall)
            )
            fieldVar
        }

        @Override
        void visitProperty(PropertyNode propertyNode) {
            FieldNode fieldNode = propertyNode.field
            if (fieldNode != null && !propertyNode.isStatic() && stereoTypeFinder.hasStereoType(fieldNode, Inject)) {
                defineBeanDefinition(concreteClass)
                ClassNode qualfierType = stereoTypeFinder.findAnnotationWithStereoType(fieldNode, Qualifier.class)?.classNode

                ClassNode fieldType = fieldNode.type
                GenericsType[] genericsTypes = fieldType.genericsTypes
                List<Object> genericTypeList = null
                if (genericsTypes != null && genericsTypes.length > 0) {
                    genericTypeList = []
                    for (genericType in genericsTypes) {
                        if (!genericType.isPlaceholder()) {
                            genericTypeList.add(resolveTypeReference(genericType.type))
                        }
                    }
                }
                else if(fieldType.isArray()){
                    genericTypeList = []
                    genericTypeList.add(resolveTypeReference(fieldType.componentType))
                }
                ClassNode declaringClass = fieldNode.declaringClass
                beanWriter.visitSetterInjectionPoint(
                        resolveTypeReference(declaringClass),
                        resolveTypeReference(qualfierType),
                        propertyNode.modifiers,
                        resolveTypeReference(fieldType),
                        fieldNode.name,
                        getSetterName(propertyNode.name),
                        genericTypeList
                )
            }
        }

        private Object resolveTypeReference(ClassNode classNode) {
            if(classNode == null) {
                return null
            }
            else {
                return classNode.isResolved() || ClassHelper.isPrimitiveType(classNode) ? classNode.typeClass : classNode.name
            }
        }

        private void addMethodInjectionPoint(ClassNode declaringClass,
                                             Expression methodName,
                                             boolean requiresReflection,
                                             VariableExpression field,
                                             Parameter... parameterTypes) {

            if (currentInjectInstance != null && !requiresReflection) {
                boolean isParentMethod = declaringClass != concreteClass
                Expression methodArgs = buildBeanLookupArguments(classX(declaringClass), methodName, parameterTypes)
                MethodCallExpression injectCall = callX(currentInjectInstance, methodName, methodArgs)
                MethodNode methodTarget = declaringClass.getMethod(methodName.text, parameterTypes)
                injectCall.setMethodTarget(methodTarget)
                BlockStatement targetBody = currentInjectBody
                int parentIndex = 2
                if (methodTarget != null && stereoTypeFinder.hasStereoType(methodTarget, PostConstruct)) {
                    if (currentStartupBody == null) {
                        currentStartupBody = block()
                    }
                    targetBody = currentStartupBody
                    parentIndex = 0
                }
                if (methodTarget != null && stereoTypeFinder.hasStereoType(methodTarget, PreDestroy)) {
                    if (currentShutdownBody == null) {
                        currentShutdownBody = block()
                    }
                    targetBody = currentShutdownBody
                    parentIndex = 0
                }

                // methods for super classes need to be injected before anything else

                if (isParentMethod) {
                    targetBody.statements.add(parentIndex, stmt(injectCall))
                } else {
                    targetBody.addStatement(
                            stmt(injectCall)
                    )
                }
            }


            ArgumentListExpression argExpr = args(methodName)
            argExpr = paramTypesToArguments(parameterTypes, argExpr)
            Expression getMethodCall = callX(declaringClass, "getDeclaredMethod", argExpr)
            def methodVar = varX('$method' + methodIndex, makeCached(Method))
            currentConstructorBody.addStatement(
                    declS(methodVar, getMethodCall)
            )
            MapExpression paramsMap = paramsToMap(parameterTypes)
            Expression qualifiers = paramsToQualifiers(parameterTypes)
            Expression generics = paramsToGenericTypes(parameterTypes)

            ArgumentListExpression addInjectionPointArgs = args(methodVar, paramsMap, qualifiers, generics, constX(requiresReflection))
            if (field != null) {
                addInjectionPointArgs.expressions.add(0, field)
            }
            MethodCallExpression addInjectionPointMethod = callThisX("addInjectionPoint", addInjectionPointArgs)
            currentConstructorBody.addStatement(
                    stmt(addInjectionPointMethod)
            )
            methodIndex++
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
            if (!beanDefinitionClassNodes.containsKey(classNode) && !classNode.isAbstract()) {
                ClassNode providerGenericType = AstGenericUtils.resolveInterfaceGenericType(classNode, Provider)
                isProvider = providerGenericType != null
                ClassNode targetClassNode = isProvider ? providerGenericType : classNode
                String packageName = classNode.packageName ? "${classNode.packageName}." : ""
                String beanDefinitionName = "${packageName}\$${classNode.nameWithoutPackage}BeanDefinition"
                AnnotationStereoTypeFinder annotationStereoTypeFinder = new AnnotationStereoTypeFinder()
                AnnotationNode scopeAnn = annotationStereoTypeFinder.findAnnotationWithStereoType(classNode, Scope.class)
                AnnotationNode singletonAnn = annotationStereoTypeFinder.findAnnotationWithStereoType(classNode, Singleton.class)
                Expression singleton = singletonAnn != null ? ConstantExpression.TRUE : ConstantExpression.FALSE

                if(isProvider) {
                    beanWriter = new BeanDefinitionWriter(
                            classNode.packageName ?: "",
                            classNode.nameWithoutPackage,
                            providerGenericType.name,
                            scopeAnn?.classNode?.name,
                            singletonAnn != null)
                }
                else {

                    beanWriter = new BeanDefinitionWriter(
                            classNode.packageName ?: "",
                            classNode.nameWithoutPackage,
                            scopeAnn?.classNode?.name,
                            singletonAnn != null)
                }
                beanDefinitionWriters.put(classNode, beanWriter)


                currentBuildBody = block()
                currentInjectBody = block()

                ClassNode[] interfaceNodes = [
                        GenericsUtils.makeClassSafeWithGenerics(BeanFactory, targetClassNode),
                        GenericsUtils.makeClassSafeWithGenerics(InjectableBeanDefinition, classNode)] as ClassNode[]

                ClassNode superClass = GenericsUtils.makeClassSafeWithGenerics(AbstractBeanDefinition, targetClassNode)
                currentBeanDef = new ClassNode(beanDefinitionName, Modifier.PUBLIC, superClass,
                        interfaceNodes, null)
                currentBeanDef.addAnnotation(new AnnotationNode(makeCached(CompileStatic)))

                currentContextParam = param(makeCached(BeanContext).plainNodeReference, '$context')
                currentResolutionContextParam = param(makeCached(BeanResolutionContext).plainNodeReference, '$resolutionContext')

                // add the build method
                Parameter[] buildMethodParams = params(
                        currentResolutionContextParam,
                        currentContextParam,
                        param(GenericsUtils.makeClassSafeWithGenerics(BeanDefinition, targetClassNode), '$definition')
                )

                Parameter[] buildDelegateMethodParams = params(
                        param(makeCached(BeanContext), '$context'),
                        param(GenericsUtils.makeClassSafeWithGenerics(BeanDefinition, targetClassNode), '$definition')
                )
                currentBeanDef.addMethod("build", Modifier.PUBLIC, targetClassNode.plainNodeReference, buildMethodParams, null, currentBuildBody)


                def newResolutionContext = ctorX(makeCached(DefaultBeanResolutionContext), args(varX(buildDelegateMethodParams[0]), varX(buildDelegateMethodParams[1])))
                currentBeanDef.addMethod("build", Modifier.PUBLIC, targetClassNode.plainNodeReference, buildDelegateMethodParams, null, block(
                        stmt(callX(varX("this"), "build", args(newResolutionContext, varX(buildDelegateMethodParams[0]), varX(buildDelegateMethodParams[1]))))
                ))


                List<ConstructorNode> constructors = classNode.getDeclaredConstructors()
                ClassExpression classNodeExpr = classX(classNode.plainNodeReference)
                BlockStatement constructorBody
                Expression scope


                if (scopeAnn != null) {
                    scope = callX(classNodeExpr, "getAnnotation", classX(scopeAnn.classNode))
                } else {
                    scope = ConstantExpression.NULL
                }


                if (constructors.isEmpty()) {
                    currentBuildInstance = varX(INSTANCE_VAR_NAME, classNode)
                    currentBuildBody.addStatement(declS(currentBuildInstance, ctorX(classNode)))
                    constructorBody = block(
                            ctorSuperS(args(scope, singleton, classNodeExpr, callX(classNodeExpr, "getConstructor")))
                    )

                    beanWriter.visitBeanDefinitionConstructor(Collections.emptyMap(), null, null)

                } else {
                    List<ConstructorNode> publicConstructors = findPublicConstructors(constructors)

                    ConstructorNode constructorNode
                    if (publicConstructors.size() == 1) {
                        constructorNode = publicConstructors[0]
                    } else {
                        constructorNode = publicConstructors.find() { it.getAnnotations(makeCached(Inject)) }
                    }
                    if (constructorNode != null) {
                        Map<String, Object> paramsToType = [:]
                        Map<String, Object> qualifierTypes = [:]
                        Map<String, List<Object>> genericTypeMap = [:]
                        Parameter[] parameters = constructorNode.parameters
                        populateParameterData(parameters, paramsToType, qualifierTypes, genericTypeMap)
                        beanWriter.visitBeanDefinitionConstructor(paramsToType, qualifierTypes, genericTypeMap)
                    } else {
                        addError("Class must have at least one public constructor in order to be a candidate for dependency injection", classNode)
                    }
                }

                // add the inject method
                def injectObjectParam = param(ClassHelper.OBJECT_TYPE, '$object')
                Parameter[] injectMethodParams = params(
                        currentResolutionContextParam,
                        currentContextParam,
                        injectObjectParam
                )
                currentBeanDef.addMethod("injectBean", Modifier.PROTECTED, classNode.plainNodeReference, injectMethodParams, null, currentInjectBody)

                def injectBeanFields = callSuperX("injectBeanFields", args(
                        varX(currentResolutionContextParam),
                        castX(makeCached(DefaultBeanContext), varX(currentContextParam)),
                        varX(injectObjectParam)
                ))

                def injectBeanMethods = callSuperX("injectBeanMethods", args(
                        varX(currentResolutionContextParam),
                        castX(makeCached(DefaultBeanContext), varX(currentContextParam)),
                        varX(injectObjectParam)
                ))
                MethodNode injectMethodTarget = DEFAULT_COMPONENT_DEFINITION.getMethod("injectBean", injectMethodParams)
                injectBeanFields.setMethodTarget(injectMethodTarget)
                currentInjectInstance = varX(INSTANCE_VAR_NAME, classNode)
                currentInjectBody.addStatement(
                        declS(currentInjectInstance, castX(classNode, varX(injectObjectParam)))
                )
                currentInjectBody.addStatement(
                        stmt(injectBeanFields)
                )
                currentInjectBody.addStatement(
                        stmt(injectBeanMethods)
                )
                currentInjectInstance = varX(INSTANCE_VAR_NAME, classNode)


                if (constructorBody != null) {
                    currentConstructorBody = constructorBody
                    currentBeanDef.addConstructor(
                            new ConstructorNode(Modifier.PUBLIC, constructorBody)
                    )

                    beanDefinitionClassNodes.put(classNode, currentBeanDef)
                }
            } else if (!classNode.isAbstract()) {
                currentBeanDef = beanDefinitionClassNodes.get(classNode)
            }
        }

        private void populateParameterData(Parameter[] parameters, Map<String, Object> paramsToType, Map<String, Object> qualifierTypes, Map<String, List<Object>> genericTypeMap) {
            for (param in parameters) {
                ClassNode parameterType = param.type
                String parameterName = param.name
                if (parameterType.isResolved()) {
                    paramsToType.put(parameterName, parameterType.typeClass)
                } else {
                    paramsToType.put(parameterName, parameterType.name)
                }
                AnnotationNode ann = stereoTypeFinder.findAnnotationWithStereoType(param, Qualifier)
                if (ann != null) {
                    if (ann.classNode.isResolved()) {
                        qualifierTypes.put(parameterName, ann.classNode.typeClass)
                    } else {
                        qualifierTypes.put(parameterName, ann.classNode.name)
                    }
                }

                GenericsType[] genericsTypes = parameterType.genericsTypes
                if (genericsTypes != null && genericsTypes.length > 0) {
                    List<Object> genericTypeList = []
                    genericTypeMap.put(parameterName, genericTypeList)
                    for (genericType in genericsTypes) {
                        if (!genericType.isPlaceholder()) {
                            genericTypeList.add(resolveTypeReference(genericType.type))
                        }
                    }
                } else if (parameterType.isArray()) {
                    List<Object> genericTypeList = []
                    genericTypeList.add(resolveTypeReference(parameterType.componentType))
                    genericTypeMap.put(parameterName, genericTypeList)
                }
            }
        }

        private ArgumentListExpression buildBeanLookupArguments(ClassExpression declaringClass, Expression methodExpression, Parameter... parameterTypes) {
            ArgumentListExpression ctorArgs = new ArgumentListExpression()

            int i = 0
            for (parameter in parameterTypes) {
                Expression expressionToAdd = buildBeanLookupExpression(declaringClass, methodExpression, parameter, i++)
                ctorArgs.addExpression(castX(parameter.type, expressionToAdd))
            }
            return ctorArgs
        }

        private Expression buildBeanLookupExpression(ClassExpression declaringClass, Expression methodExpression, Parameter parameter, int parameterIndex) {
            ClassNode type = parameter.type
            Expression expressionToAdd
            boolean isConstructor = methodExpression instanceof ClassExpression
            boolean isProvider = AstClassUtils.implementsInterface(type, Provider) && type.genericsTypes
            boolean isIterable = (AstClassUtils.implementsInterface(type, Iterable) && type.genericsTypes) || type.isArray()
            String lookMethodName
            if (isIterable) {
                lookMethodName = isConstructor ? "getBeansOfTypeForConstructorArgument" : "getBeansOfTypeForMethodArgument"
            } else if (isProvider) {
                lookMethodName = isConstructor ? "getBeanProviderForConstructorArgument" : "getBeanProviderForMethodArgument"
            } else {
                lookMethodName = isConstructor ? "getBeanForConstructorArgument" : "getBeanForMethodArgument"
            }


            ArgumentListExpression lookupArgs = args(varX(currentResolutionContextParam), varX(currentContextParam))
            if (isProvider) {
                lookupArgs.addExpression(classX(type.genericsTypes[0].type.plainNodeReference))
            }
            if (isConstructor) {
                lookupArgs.addExpression(constX(parameterIndex))
            } else {
                lookupArgs.addExpression(constX(methodIndex))
                lookupArgs.addExpression(constX(parameterIndex))
            }
            expressionToAdd = callX(varX("this"), lookMethodName, lookupArgs)
            return expressionToAdd
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

        private ArgumentListExpression paramTypesToArguments(Parameter[] parameters) {
            ArgumentListExpression args = new ArgumentListExpression()
            return paramTypesToArguments(parameters, args)
        }


        private ArgumentListExpression paramsToArguments(Parameter[] parameters) {
            ArgumentListExpression args = new ArgumentListExpression()
            for (p in parameters) {
                args.addExpression(varX(p))
            }
            return args
        }

        private MapExpression paramsToMap(Parameter[] parameters) {
            MapExpression map = new MapExpression()
            for (p in parameters) {
                map.addMapEntryExpression(constX(p.name), classX(p.type.plainNodeReference))
            }
            return map
        }

        private Expression paramsToQualifiers(Parameter[] parameters) {
            MapExpression map = null
            AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()
            for (p in parameters) {
                AnnotationNode ann = stereoTypeFinder.findAnnotationWithStereoType(p, Qualifier)
                if (ann != null) {
                    if (map == null) map = new MapExpression()
                    map.addMapEntryExpression(constX(p.name), classX(ann.classNode))
                }
            }
            if (map != null) {
                return map
            } else {
                return ConstantExpression.NULL
            }
        }

        private Expression paramsToGenericTypes(Parameter[] parameters) {
            MapExpression map = null
            for (p in parameters) {
                GenericsType[] genericsTypes = p.type.genericsTypes
                if (genericsTypes != null && genericsTypes.length > 0) {
                    if (map == null) map = new MapExpression()
                    ListExpression listExpression = new ListExpression()
                    for (genericType in genericsTypes) {
                        listExpression.addExpression(classX(genericType.type.plainNodeReference))
                    }
                    map.addMapEntryExpression(constX(p.name), listExpression)
                } else if (p.type.isArray()) {
                    if (map == null) map = new MapExpression()
                    ListExpression listExpression = new ListExpression([classX(p.type.componentType.plainNodeReference)] as List<Expression>)
                    map.addMapEntryExpression(constX(p.name), listExpression)
                }
            }
            if (map != null) {
                return map
            } else {
                return ConstantExpression.NULL
            }
        }

        private ArgumentListExpression paramTypesToArguments(Parameter[] parameters, ArgumentListExpression args) {
            for (p in parameters) {
                args.addExpression(classX(p.type))
            }
            return args
        }
    }

}
