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

package io.micronaut.cli.profile.commands.script

import groovy.transform.CompileStatic
import io.micronaut.cli.profile.CommandDescription
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import java.lang.reflect.Modifier

/**
 * Transformation applied to command scripts
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
@CompileStatic
class GroovyScriptCommandTransform implements ASTTransformation {
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        for (ClassNode cNode in source.AST.classes) {
            if (cNode.superClass.name == "io.micronaut.cli.profile.commands.script.GroovyScriptCommand")
                new CommandScriptTransformer(source, cNode).visitClass(cNode)
        }
    }

    static class CommandScriptTransformer extends ClassCodeVisitorSupport {
        SourceUnit sourceUnit
        ClassNode classNode

        CommandScriptTransformer(SourceUnit sourceUnit, ClassNode classNode) {
            this.sourceUnit = sourceUnit
            this.classNode = classNode
        }

        @Override
        void visitMethodCallExpression(MethodCallExpression call) {
            if (call.methodAsString == 'description' && (call.arguments instanceof ArgumentListExpression)) {
                def constructorBody = new BlockStatement()
                def defaultConstructor = getDefaultConstructor(classNode)
                if (defaultConstructor == null) {
                    defaultConstructor = new ConstructorNode(Modifier.PUBLIC, constructorBody)
                    classNode.addConstructor(defaultConstructor)
                } else {
                    constructorBody.addStatement(defaultConstructor.getCode())
                    defaultConstructor.setCode(constructorBody)
                }


                ArgumentListExpression existing = (ArgumentListExpression) call.arguments

                def arguments = existing.expressions
                if (arguments.size() == 2) {
                    def constructorArgs = new ArgumentListExpression()
                    constructorArgs.addExpression(new VariableExpression("name"))
                    def secondArg = arguments.get(1)
                    Expression constructDescription = new ConstructorCallExpression(ClassHelper.make(CommandDescription), constructorArgs)
                    if (secondArg instanceof ClosureExpression) {
                        constructorArgs.addExpression(arguments.get(0))
                        ClosureExpression closureExpression = (ClosureExpression) secondArg
                        def body = closureExpression.code
                        if (body instanceof BlockStatement) {
                            BlockStatement bodyBlock = (BlockStatement) body
                            for (Statement s in bodyBlock.statements) {
                                if (s instanceof ExpressionStatement) {
                                    ExpressionStatement es = (ExpressionStatement) s

                                    def expr = es.expression
                                    if (expr instanceof MethodCallExpression) {
                                        MethodCallExpression mce = (MethodCallExpression) expr
                                        def methodCallArgs = mce.getArguments()

                                        switch (mce.methodAsString) {
                                            case 'usage':
                                                if (methodCallArgs instanceof ArgumentListExpression) {
                                                    constructorArgs.addExpression(((ArgumentListExpression) methodCallArgs).getExpression(0))
                                                }

                                                break
                                            default:
                                                constructDescription = new MethodCallExpression(constructDescription, mce.methodAsString, methodCallArgs)
                                                break

                                        }
                                    }
                                }
                            }
                        }

                    } else {
                        constructorArgs.expressions.addAll(arguments)
                    }

                    def assignDescription = new MethodCallExpression(new VariableExpression("this"), "setDescription", constructDescription)
                    constructorBody.addStatement(new ExpressionStatement(assignDescription))
                }


            } else {
                super.visitMethodCallExpression(call)
            }
        }
    }

    public static ConstructorNode getDefaultConstructor(ClassNode classNode) {
        for (ConstructorNode cons in classNode.getDeclaredConstructors()) {
            if (cons.getParameters().length == 0) {
                return cons
            }
        }
        return null
    }

}
