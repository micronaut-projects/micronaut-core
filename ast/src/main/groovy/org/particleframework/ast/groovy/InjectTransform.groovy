package org.particleframework.ast.groovy

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
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
import org.particleframework.context.Context
import org.particleframework.context.DefaultComponentDefinitionClass
import org.particleframework.context.DefaultComponentDefinition
import org.particleframework.inject.ComponentDefinition
import org.particleframework.inject.ComponentDefinitionClass
import org.particleframework.inject.ComponentFactory

import javax.inject.Inject
import javax.inject.Scope
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

    public static final String FACTORY_INSTANCE_VAR_NAME = '$instance'
    CompilationUnit unit

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()

        InjectVisitor injectVisitor = new InjectVisitor(source)
        for (ClassNode classNode in moduleNode.getClasses()) {
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
                    ctorSuperS(args(classX(newClass)))
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

        ClassNode currentComponentDef = null
        BlockStatement currentConstructorBody = null
        BlockStatement currentFactoryBody = null
        Parameter currentContextParam = null
        VariableExpression currentFactoryInstance = null
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
            if(currentFactoryBody != null) {
                def injectArgs = args( varX(currentContextParam), currentFactoryInstance, ConstantExpression.TRUE )
                currentFactoryBody.addStatement(
                    stmt( callX(varX("this"), "injectBean", injectArgs) )
                )
            }
        }

        @Override
        protected void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
            if (stereoTypeFinder.hasStereoType(node, Inject)) {
                defineComponentDefinitionClass(node.declaringClass)

                if (!isConstructor && currentConstructorBody != null) {
                    Expression methodName = constX(node.name)
                    ClassExpression declaringClass = classX(node.declaringClass.plainNodeReference)
                    addMethodInjectionPoint(declaringClass, methodName, node.parameters)
                }
            }

        }

        @Override
        void visitField(FieldNode fieldNode) {
            if (stereoTypeFinder.hasStereoType(fieldNode, Inject) && fieldNode.declaringClass.getProperty(fieldNode.getName()) == null) {
                defineComponentDefinitionClass(fieldNode.declaringClass)
                if (currentConstructorBody != null) {
                    MethodCallExpression getFieldCall = callX(
                            classX(fieldNode.declaringClass.plainNodeReference),
                            "getDeclaredField",
                            args(constX(fieldNode.name)))
                    MethodCallExpression addInjectionPointMethod = callX(varX("this"),"addInjectionPoint",getFieldCall)
                    currentConstructorBody.addStatement(
                        stmt(addInjectionPointMethod)
                    )
                }
            }
        }

        @Override
        void visitProperty(PropertyNode propertyNode) {
            if(propertyNode.field != null && stereoTypeFinder.hasStereoType(propertyNode.field, Inject)) {
                defineComponentDefinitionClass(propertyNode.declaringClass)
                if (currentConstructorBody != null) {
                    ConstantExpression setterName = constX(getSetterName(propertyNode.name))
                    ClassExpression declaringClass = classX(propertyNode.declaringClass.plainNodeReference)
                    Expression propertyType = classX(propertyNode.type)

                    addMethodInjectionPoint(declaringClass, setterName, param(propertyType.type, propertyNode.name))
                }
            }
        }

        private void addMethodInjectionPoint(ClassExpression declaringClass, Expression methodName, Parameter... parameterTypes) {
            if (currentFactoryInstance != null) {
                Expression methodArgs = buildBeanLookupArguments(declaringClass, methodName, parameterTypes)
                currentFactoryBody.addStatement(
                    stmt(callX(currentFactoryInstance, methodName, methodArgs ))
                )
            }

            ArgumentListExpression argExpr = args(methodName)
            argExpr = paramTypesToArguments(parameterTypes, argExpr)
            MethodCallExpression getMethodCall = callX(declaringClass, "getMethod", argExpr)
            Expression methodVar = varX('$method', makeCached(Method))
            currentConstructorBody.addStatement(
                    declS(methodVar, getMethodCall)
            )
            MethodCallExpression addInjectionPointMethod = callX(varX("this"), "addInjectionPoint", methodVar)
            currentConstructorBody.addStatement(
                    stmt(addInjectionPointMethod)
            )
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit
        }

        private void defineComponentDefinitionClass(ClassNode classNode) {
            String componentDefinitionName = classNode.name + "ComponentDefinition"
            if (!componentDefinitionClassNodes.containsKey(classNode)) {
                currentFactoryBody = block()
                currentComponentDef = new ClassNode(componentDefinitionName, Modifier.PUBLIC, GenericsUtils.makeClassSafeWithGenerics(DefaultComponentDefinition, classNode),
                                                    [GenericsUtils.makeClassSafeWithGenerics(ComponentFactory, classNode)] as ClassNode[], null)
                currentContextParam = param(makeCached(Context), '$context')
                currentComponentDef.addMethod("build", Modifier.PUBLIC, classNode.plainNodeReference, params(currentContextParam, param(GenericsUtils.makeClassSafeWithGenerics(ComponentDefinition, classNode), '$definition')), null, currentFactoryBody)
                currentComponentDef.addAnnotation(new AnnotationNode(makeCached(CompileStatic)))
                List<ConstructorNode> constructors = classNode.getDeclaredConstructors()
                ClassExpression classNodeExpr = classX(classNode.plainNodeReference)
                BlockStatement constructorBody
                if (constructors.isEmpty()) {
                    currentFactoryInstance = varX(FACTORY_INSTANCE_VAR_NAME, classNode)
                    currentFactoryBody.addStatement(declS(currentFactoryInstance, ctorX(classNode)))
                    constructorBody = block(
                        ctorSuperS(args(classNodeExpr, callX(classNodeExpr, "getConstructor")))
                    )

                } else {
                    List<ConstructorNode> publicConstructors = findPublicConstructors(constructors)
                    if(publicConstructors.size() == 1) {
                        MethodNode constructorNode = publicConstructors[0]
                        ArgumentListExpression ctorTypeArgs = paramTypesToArguments(constructorNode.parameters)
                        ArgumentListExpression ctorArgs = buildBeanLookupArguments(classNodeExpr, classNodeExpr, constructorNode.parameters)
                        currentFactoryInstance = varX(FACTORY_INSTANCE_VAR_NAME, classNode)
                        currentFactoryBody.addStatement(declS(currentFactoryInstance, ctorX(classNode, ctorArgs)))
                        constructorBody = block(
                                ctorSuperS(args(classNodeExpr, callX(classNodeExpr, "getConstructor", ctorTypeArgs)))
                        )
                    }
                    else {
                        addError("Class must have at least one public constructor in order to be a candidate for dependency injection", classNode)
                    }
                }

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
            for (parameter in parameterTypes) {
                Expression expressionToAdd = buildBeanLookupExpression(declaringClass, methodExpression, parameter)
                ctorArgs.addExpression(expressionToAdd)
            }
            return ctorArgs
        }

        private Expression buildBeanLookupExpression(ClassExpression declaringClass, Expression methodExpression, Parameter parameter) {
            ClassNode type = parameter.type
            Expression expressionToAdd
            boolean isConstructor = methodExpression instanceof ClassExpression
            String lookMethodName = isConstructor ? "getBeanForConstructorArgument" : "getBeanForMethodArgument"
            if (AstClassUtils.implementsInterface(type, Iterable) && type.genericsTypes) {
                Expression getBeansOfTypeCall = callX(varX(currentContextParam), "getBeansOfType", classX(type.genericsTypes[0].type))
                expressionToAdd = castX(type, getBeansOfTypeCall)
            } else if (type?.isArray()) {
                Expression getBeansOfTypeCall = callX(varX(currentContextParam), "getBeansOfType", classX(type.componentType))
                expressionToAdd = castX(type, getBeansOfTypeCall)

            } else {
                ArgumentListExpression lookupArgs = args(varX(currentContextParam), classX(parameter.type), declaringClass)
                if(!isConstructor) {
                    lookupArgs.addExpression(methodExpression)
                }
                lookupArgs.addExpression(constX(parameter.name))
                expressionToAdd = callX(varX("this"), lookMethodName, lookupArgs)
            }
            expressionToAdd
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

        private ArgumentListExpression paramTypesToArguments(Parameter[] parameters, ArgumentListExpression args) {
            for (p in parameters) {
                args.addExpression(classX(p.type))
            }
            return args
        }
    }

}
