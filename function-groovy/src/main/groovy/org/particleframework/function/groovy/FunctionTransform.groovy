/*
 * Copyright 2017 original authors
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
package org.particleframework.function.groovy

import groovy.transform.CompileStatic
import groovy.transform.Field
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.FieldASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.sc.transformers.StaticCompilationTransformer
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor
import org.particleframework.ast.groovy.InjectTransform
import org.particleframework.ast.groovy.annotation.AnnotationStereoTypeFinder
import org.particleframework.ast.groovy.utils.AstMessageUtils
import org.particleframework.ast.groovy.utils.AstUtils
import org.particleframework.context.ApplicationContext
import org.particleframework.function.executor.FunctionInitializer

import javax.inject.Inject
import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Transforms a Groovy script into a function
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class FunctionTransform implements ASTTransformation{
    public static final ClassNode FIELD_TYPE = ClassHelper.make(Field)
    AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder();
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {

        def uri = source.getSource().getURI()
        if(uri != null) {
            def file = uri.toString()
            if(!file.contains("src/main/groovy") || file.endsWith("logback.groovy") || file.endsWith("application.groovy") || file ==~ (/\S+\/application-\S+.groovy/)) {
                return
            }
        }
        for(node in source.getAST().classes) {
            if(node.isScript()) {
                node.setSuperClass(ClassHelper.makeCached(FunctionInitializer))
                MethodNode functionMethod = node.methods.find() { method -> !method.isAbstract() &&  !method.isStatic() && method.isPublic() && method.name != 'run' }
                if(functionMethod == null) {
                    AstMessageUtils.error(source, node, "Function must have at least one public method")
                }
                else {


                    MethodNode runMethod = node.getMethod("run", AstUtils.ZERO_PARAMETERS)
                    node.removeMethod(runMethod)
                    MethodNode mainMethod = node.getMethod("main", new Parameter(ClassHelper.make(([] as String[]).class), "args"))
                    Parameter argParam = mainMethod.getParameters()[0]
                    def thisInstance = varX('$this')
                    def functionCall = callX(thisInstance, functionMethod.getName(), args(callX(varX("it"), "get", args(classX(functionMethod.parameters[0].type.plainNodeReference)))))
                    def closureExpression = closureX(stmt(functionCall))
                    mainMethod.variableScope.putDeclaredVariable(thisInstance)
                    closureExpression.setVariableScope(mainMethod.variableScope)
                    mainMethod.setCode(
                            block(
                                declS(thisInstance, ctorX(node)),
                                stmt(callX(thisInstance, "run", args(varX(argParam), closureExpression)))
                            )
                    )
                    new StaticCompilationTransformer(source, new StaticTypeCheckingVisitor(source, node)).visitMethod(mainMethod)
                    def code = runMethod.getCode()
                    if(code instanceof BlockStatement) {
                        BlockStatement bs = (BlockStatement)code
                        for(st in bs.statements) {
                            if(st instanceof ExpressionStatement) {
                                ExpressionStatement es = (ExpressionStatement)st
                                Expression exp = es.expression
                                if(exp instanceof DeclarationExpression) {
                                    DeclarationExpression de = (DeclarationExpression)exp
                                    def initial = de.getVariableExpression().getInitialExpression()
                                    if ( initial == null) {
                                        de.addAnnotation(new AnnotationNode(ClassHelper.make(Inject)))
                                        new FieldASTTransformation().visit([new AnnotationNode(FIELD_TYPE), de] as ASTNode[], source)
                                    }
                                }
                            }
                        }
                    }

                    node.addMethod(new MethodNode(
                            "injectThis",
                            Modifier.PROTECTED,
                            ClassHelper.VOID_TYPE,
                            params(param(ClassHelper.make(ApplicationContext),"ctx")),
                            null,
                            block()
                    ))

                    ConstructorNode constructorNode = new ConstructorNode(Modifier.PUBLIC, block(
                            stmt(
                                    callX(varX("applicationContext"), "inject", varX("this"))
                            )
                    ))
                    constructorNode.addAnnotation(new AnnotationNode(ClassHelper.make(Inject)))
                    node.declaredConstructors.clear()
                    node.addConstructor(constructorNode)

                    new InjectTransform().visit(nodes, source)
                }

            }
        }
    }
}
