package org.particleframework.ast.groovy

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
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
import org.particleframework.ast.groovy.utils.AstClassUtils
import org.particleframework.ast.groovy.utils.AstGenericUtils
import org.particleframework.context.ComponentResolutionContext
import org.particleframework.context.Context
import org.particleframework.context.DefaultComponentDefinitionClass
import org.particleframework.context.DefaultComponentDefinition
import org.particleframework.context.DefaultComponentResolutionContext
import org.particleframework.inject.ComponentDefinition
import org.particleframework.inject.ComponentDefinitionClass
import org.particleframework.inject.ComponentFactory
import org.particleframework.inject.InjectableComponentDefinition

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Scope
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

    private static final String FACTORY_INSTANCE_VAR_NAME = '$instance'
    private static final ClassNode DEFAULT_COMPONENT_DEFINITION = makeCached(DefaultComponentDefinition)

    CompilationUnit unit

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()

        InjectVisitor injectVisitor = new InjectVisitor(source)
        for (ClassNode classNode in moduleNode.getClasses()) {
            if(classNode.isAbstract()) continue
            injectVisitor.visitClass(classNode)
        }
        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator()
        for (entry in injectVisitor.componentDefinitionClassNodes) {
            ClassNode newClass = entry.value
            ClassNode targetClass =entry.key
            moduleNode.addClass(newClass)
            ClassNode componentDefinitionClassSuperClass = GenericsUtils.makeClassSafeWithGenerics(DefaultComponentDefinitionClass, targetClass)
            ClassNode componentDefinitionClass = new ClassNode(newClass.name + "Class", Modifier.PUBLIC, componentDefinitionClassSuperClass)

            componentDefinitionClass.addAnnotation(new AnnotationNode(makeCached(CompileStatic)))
            componentDefinitionClass.addConstructor(
                new ConstructorNode(Modifier.PUBLIC, block(
                    ctorSuperS(args(classX(newClass))),
                    // set the meta class to null to save memory
                    stmt(callX(varX("this"), "setMetaClass", ConstantExpression.NULL))
                ))
            )
            componentDefinitionClass.setModule(moduleNode)
            generator.generate(componentDefinitionClass, ComponentDefinitionClass)
            moduleNode.addClass(componentDefinitionClass)
        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }

    private static class InjectVisitor extends ClassCodeVisitorSupport {
        final SourceUnit sourceUnit
        final Map<ClassNode, ClassNode> componentDefinitionClassNodes = [:]

        boolean isProvider = false
        ClassNode currentComponentDef = null
        BlockStatement currentConstructorBody = null
        BlockStatement currentBuildBody = null
        BlockStatement currentInjectBody = null
        Parameter currentContextParam = null
        int methodIndex = 0
        Parameter currentResolutionContextParam = null
        VariableExpression currentBuildInstance = null
        VariableExpression currentInjectInstance = null
        AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()

        InjectVisitor(SourceUnit sourceUnit) {
            this.sourceUnit = sourceUnit
        }

        @Override
        void visitClass(ClassNode node) {
            if (stereoTypeFinder.hasStereoType(node, Scope)) {
                defineComponentDefinitionClass(node)
            }
            super.visitClass(node)
            if(currentBuildBody != null && !currentComponentDef.getNodeMetaData("injected")) {
                def thisX = varX("this")
                // set the meta class to null to save memory
                if(currentConstructorBody != null) {
                    currentConstructorBody.addStatement(
                            stmt(callX(thisX, "setMetaClass", ConstantExpression.NULL))
                    )
                }
                def injectBeanArgs = args( varX(currentResolutionContextParam), varX(currentContextParam), currentInjectInstance, ConstantExpression.TRUE )
                currentBuildBody.addStatement(
                    stmt(callX(thisX, "injectBean", injectBeanArgs))
                )
                if(isProvider) {
                    currentBuildBody.addStatement(
                        returnS(callX(currentBuildInstance, "get"))
                    )
                }
                currentInjectBody.addStatement(
                    returnS(currentInjectInstance)
                )
                currentComponentDef.putNodeMetaData("injected", Boolean.TRUE)
            }
        }

        @Override
        protected void visitConstructorOrMethod(MethodNode methodNode, boolean isConstructor) {
            if (stereoTypeFinder.hasStereoType(methodNode, Inject)) {
                defineComponentDefinitionClass(methodNode.declaringClass)

                if (!isConstructor && currentConstructorBody != null) {
                    if(!methodNode.isStatic()) {

                        Expression methodName = constX(methodNode.name)
                        ClassExpression declaringClass = classX(methodNode.declaringClass.plainNodeReference)
                        addMethodInjectionPoint(declaringClass, methodName, methodNode.isPublic(), methodNode.parameters)
                    }
                    else {
                        // TODO: handle static method injection?
                    }
                }
            }

        }

        @Override
        void visitField(FieldNode fieldNode) {
            if (stereoTypeFinder.hasStereoType(fieldNode, Inject) && fieldNode.declaringClass.getProperty(fieldNode.getName()) == null) {
                defineComponentDefinitionClass(fieldNode.declaringClass)
                if (currentConstructorBody != null) {
                    if(!fieldNode.isStatic()) {

                        MethodCallExpression getFieldCall = callX(
                                classX(fieldNode.declaringClass.plainNodeReference),
                                "getDeclaredField",
                                args(constX(fieldNode.name)))
                        MethodCallExpression addInjectionPointMethod = callX(varX("this"),"addInjectionPoint",getFieldCall)
                        currentConstructorBody.addStatement(
                                stmt(addInjectionPointMethod)
                        )
                    }
                    else {
                        // TODO: Handle static field injection?
                    }
                }
            }
        }

        @Override
        void visitProperty(PropertyNode propertyNode) {
            if(propertyNode.field != null && stereoTypeFinder.hasStereoType(propertyNode.field, Inject)) {
                if(!propertyNode.isStatic()) {
                    defineComponentDefinitionClass(propertyNode.declaringClass)
                    if (currentConstructorBody != null) {
                        ConstantExpression setterName = constX(getSetterName(propertyNode.name))
                        ClassExpression declaringClass = classX(propertyNode.declaringClass.plainNodeReference)
                        Expression propertyType = classX(propertyNode.type)

                        addMethodInjectionPoint(declaringClass, setterName, propertyNode.isPublic(), param(propertyType.type, propertyNode.name))
                    }
                }
                else {
                    // TODO: handle static property injection?
                }
            }
        }

        private void addMethodInjectionPoint(ClassExpression declaringClass, Expression methodName, boolean isPublic, Parameter... parameterTypes) {
            if (currentInjectInstance != null && isPublic) {
                Expression methodArgs = buildBeanLookupArguments(declaringClass, methodName, parameterTypes)
                methodIndex++
                def injectCall = callX(currentInjectInstance, methodName, methodArgs)
                def methodTarget = declaringClass.type.getMethod(methodName.text, parameterTypes)
                injectCall.setMethodTarget(methodTarget)
                currentInjectBody.addStatement(
                    stmt(injectCall)
                )
            }


            ArgumentListExpression argExpr = args(methodName)
            argExpr = paramTypesToArguments(parameterTypes, argExpr)
            MethodCallExpression getMethodCall = callX(declaringClass, "getDeclaredMethod", argExpr)
            MapExpression paramsMap = paramsToMap(parameterTypes)
            MethodCallExpression addInjectionPointMethod = callX(varX("this"), "addInjectionPoint", args(getMethodCall,paramsMap) )
            currentConstructorBody.addStatement(
                    stmt(addInjectionPointMethod)
            )
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit
        }

        private void defineComponentDefinitionClass(ClassNode classNode) {
            if (!componentDefinitionClassNodes.containsKey(classNode)) {
                ClassNode providerGenericType = AstGenericUtils.resolveInterfaceGenericType(classNode, Provider)
                isProvider = providerGenericType != null
                ClassNode targetClassNode = isProvider ? providerGenericType : classNode
                String componentDefinitionName = classNode.name + "ComponentDefinition"
                currentBuildBody = block()
                currentInjectBody = block()
                ClassNode[] interfaceNodes = [GenericsUtils.makeClassSafeWithGenerics(ComponentFactory, targetClassNode), GenericsUtils.makeClassSafeWithGenerics(InjectableComponentDefinition, classNode)] as ClassNode[]
                ClassNode superClass = GenericsUtils.makeClassSafeWithGenerics(DefaultComponentDefinition, targetClassNode)
                currentComponentDef = new ClassNode(componentDefinitionName, Modifier.PUBLIC, superClass,
                        interfaceNodes, null)

                currentContextParam = param(makeCached(Context).plainNodeReference, '$context')
                currentResolutionContextParam = param(makeCached(ComponentResolutionContext).plainNodeReference, '$resolutionContext')

                // add the build method
                Parameter[] buildMethodParams = params(
                    currentResolutionContextParam,
                    currentContextParam,
                    param(GenericsUtils.makeClassSafeWithGenerics(ComponentDefinition, targetClassNode), '$definition')
                )

                Parameter[] buildDelegateMethodParams = params(
                    param(makeCached(Context), '$context'),
                    param(GenericsUtils.makeClassSafeWithGenerics(ComponentDefinition, targetClassNode), '$definition')
                )
                currentComponentDef.addMethod("build", Modifier.PUBLIC, targetClassNode.plainNodeReference, buildMethodParams, null, currentBuildBody)


                def newResolutionContext = ctorX(makeCached(DefaultComponentResolutionContext), args(varX(buildDelegateMethodParams[0]), varX(buildDelegateMethodParams[1])))
                currentComponentDef.addMethod("build", Modifier.PUBLIC, targetClassNode.plainNodeReference, buildDelegateMethodParams, null, block(
                    stmt( callX(varX("this"),"build", args(newResolutionContext, varX(buildDelegateMethodParams[0]), varX(buildDelegateMethodParams[1])) ) )
                ))

                currentComponentDef.addAnnotation(new AnnotationNode(makeCached(CompileStatic)))
                List<ConstructorNode> constructors = classNode.getDeclaredConstructors()
                ClassExpression classNodeExpr = classX(classNode.plainNodeReference)
                BlockStatement constructorBody

                if (constructors.isEmpty()) {
                    currentBuildInstance = varX(FACTORY_INSTANCE_VAR_NAME, classNode)
                    currentBuildBody.addStatement(declS(currentBuildInstance, ctorX(classNode)))
                    constructorBody = block(
                        ctorSuperS(args(classNodeExpr, callX(classNodeExpr, "getConstructor"), ctorX(makeCached(LinkedHashMap), constX(0))) )
                    )

                } else {
                    List<ConstructorNode> publicConstructors = findPublicConstructors(constructors)

                    ConstructorNode constructorNode
                    if(publicConstructors.size() == 1) {
                        constructorNode = publicConstructors[0]
                    }
                    else {
                        constructorNode = publicConstructors.find() { it.getAnnotations(makeCached(Inject))}
                    }
                    if(constructorNode != null) {
                        ArgumentListExpression ctorTypeArgs = paramTypesToArguments(constructorNode.parameters)
                        MapExpression ctorParamsMap = paramsToMap(constructorNode.parameters)
                        ArgumentListExpression ctorArgs =  buildBeanLookupArguments(classNodeExpr, classNodeExpr, constructorNode.parameters)
                        currentBuildInstance = varX(FACTORY_INSTANCE_VAR_NAME, classNode.plainNodeReference)
                        currentBuildBody.addStatement(declS(currentBuildInstance, ctorX(classNode.plainNodeReference, ctorArgs)))
                        constructorBody = block(
                            ctorSuperS(args(classNodeExpr, callX(classNodeExpr, "getConstructor", ctorTypeArgs), ctorParamsMap ))
                        )
                    }
                    else {
                        addError("Class must have at least one public constructor in order to be a candidate for dependency injection", classNode)
                    }
                }

                // add the inject method
                def injectObjectParam = param(ClassHelper.OBJECT_TYPE, '$object')
                Parameter[] injectParams = params(
                        currentResolutionContextParam,
                        currentContextParam,
                        injectObjectParam,
                        param(ClassHelper.boolean_TYPE, "onlyPublic")
                )
                currentComponentDef.addMethod("injectBean", Modifier.PUBLIC, classNode.plainNodeReference, injectParams, null, currentInjectBody)
                def superCall = callSuperX("injectBean", paramsToArguments(injectParams))
                MethodNode injectMethodTarget = DEFAULT_COMPONENT_DEFINITION.getMethod("injectBean", injectParams)
                superCall.setMethodTarget(injectMethodTarget)
                currentInjectBody.addStatement(
                    stmt(superCall)
                )
                currentInjectInstance = varX(FACTORY_INSTANCE_VAR_NAME, classNode)

                currentInjectBody.addStatement(
                    declS(currentInjectInstance, castX(classNode, varX(injectObjectParam)))
                )

                if (constructorBody != null) {
                    currentConstructorBody = constructorBody
                    currentComponentDef.addConstructor(
                        new ConstructorNode(Modifier.PUBLIC, constructorBody)
                    )

                    componentDefinitionClassNodes.put(classNode, currentComponentDef)
                }
            } else {
                currentComponentDef = componentDefinitionClassNodes.get(classNode)
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
            if (AstClassUtils.implementsInterface(type, Iterable) && type.genericsTypes) {
                Expression getBeansOfTypeCall = callX(varX(currentContextParam), "getBeansOfType", classX(type.genericsTypes[0].type))
                expressionToAdd = castX(type, getBeansOfTypeCall)
            } else if (type?.isArray()) {
                Expression getBeansOfTypeCall = callX(varX(currentContextParam), "getBeansOfType", classX(type.componentType))
                expressionToAdd = castX(type, getBeansOfTypeCall)

            } else {
                boolean isConstructor = methodExpression instanceof ClassExpression
                boolean isProvider = AstClassUtils.implementsInterface(type, Provider) && type.genericsTypes
                String lookMethodName
                if(isProvider) {
                    lookMethodName = isConstructor ? "getBeanProviderForConstructorArgument" : "getBeanProviderForMethodArgument"
                }
                else {
                    lookMethodName = isConstructor ? "getBeanForConstructorArgument" : "getBeanForMethodArgument"
                }


                ArgumentListExpression lookupArgs = args(varX(currentResolutionContextParam), varX(currentContextParam) )
                if(isProvider) {
                    lookupArgs.addExpression(classX(type.genericsTypes[0].type.plainNodeReference))
                }
                if(isConstructor) {
                    lookupArgs.addExpression(constX(parameterIndex))
                }
                else {
                    lookupArgs.addExpression(constX(methodIndex))
                    lookupArgs.addExpression(constX(parameterIndex))
                }
                expressionToAdd = callX(varX("this"), lookMethodName, lookupArgs)
            }
            return expressionToAdd
        }

        private List<ConstructorNode> findPublicConstructors(List<ConstructorNode> constructorNodes) {
            List<ConstructorNode> publicConstructors = []
            for(node in constructorNodes ) {
                if(Modifier.isPublic(node.modifiers)) {
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
            for(p in parameters) {
                map.addMapEntryExpression( constX(p.name), classX(p.type.plainNodeReference) )
            }
            return map
        }

        private ArgumentListExpression paramTypesToArguments(Parameter[] parameters, ArgumentListExpression args) {
            for (p in parameters) {
                args.addExpression(classX(p.type))
            }
            return args
        }
    }

}
