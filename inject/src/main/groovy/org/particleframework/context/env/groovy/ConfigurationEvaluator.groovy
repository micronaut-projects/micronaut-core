package org.particleframework.context.env.groovy

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Evaluates type safe configurations converting property paths to calls to setProperty(..) and returning a map of all assigned configuration values
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ConfigurationEvaluator {

    /**
     * Evaluate the configuration from the given input stream
     * @param inputStream The input stream
     * @return The configuration values
     */
    Map<String, Object> evaluate(InputStream inputStream) {
        Reader reader = new InputStreamReader(inputStream, "UTF-8")
        evaluate(reader)
    }

    /**
     * Evaluate the configuration from the given string
     *
     * @param string The string
     * @return The configuration values
     */
    Map<String, Object> evaluate(String string) {
        evaluate(new StringReader(string))
    }

    /**
     * Evaluates the configuration from the given reader
     * @param reader The reader
     * @return The configuration values
     */
    Map<String, Object> evaluate(Reader reader) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.addCompilationCustomizers(
            new ASTTransformationCustomizer(CompileStatic.class),
            new ASTTransformationCustomizer(new ConfigTransform())
        )

        GroovyShell shell = new GroovyShell(configuration)
        Script script = shell.parse(reader)
        script.run()
        return script.getBinding().getVariables()
    }

    @GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
    private static class ConfigTransform implements ASTTransformation {
        @Override
        void visit(ASTNode[] nodes, SourceUnit source) {
            List<ClassNode> classNodes = source.getAST().classes
            ClassNode scriptClassNode = classNodes.find { it.script }
            if(scriptClassNode != null) {
                MethodNode runMethod = scriptClassNode.getMethod('run', [] as Parameter[])
                if(runMethod != null) {
                    new SetPropertyTransformer(source).visitMethod(runMethod)
                }
            }
        }
    }

    private static class SetPropertyTransformer extends ClassCodeExpressionTransformer {
        final SourceUnit sourceUnit

        String nestedPath = ""

        SetPropertyTransformer(SourceUnit sourceUnit) {
            this.sourceUnit = sourceUnit
        }

        @Override
        Expression transform(Expression exp) {
            if(exp instanceof BinaryExpression) {
                BinaryExpression be = (BinaryExpression)exp
                if(be.operation.type == ASSIGN.type) {
                    Expression left = be.leftExpression
                    Expression right = be.rightExpression
                    if(left instanceof VariableExpression) {
                        def varX = (VariableExpression) left
                        if(varX.accessedVariable.isDynamicTyped()) {
                            String path = "${nestedPath}${varX.name}"
                            return callThisX("setProperty", args(constX(path), right))
                        }
                    }
                    else if(left instanceof PropertyExpression) {
                        PropertyExpression propX = (PropertyExpression)left
                        String path = buildPath(propX)
                        if(path != null) {
                            return callThisX("setProperty", args(constX(path), right))
                        }

                    }
                }
            }
            else if(exp instanceof MethodCallExpression) {
                MethodCallExpression methodX = (MethodCallExpression)exp
                Expression argsX = methodX.arguments
                ClosureExpression closureX = findSingleClosure(argsX)
                if(closureX != null) {
                    String currentNestedPath = nestedPath
                    try {
                        nestedPath = "${nestedPath ? nestedPath : ''}${methodX.methodAsString}."
                        visitClosureExpression(closureX)
                    } finally {
                        nestedPath = currentNestedPath
                    }
                    return callThisX("with", args(closureX))
                }
            }
            return super.transform(exp)
        }

        private ClosureExpression findSingleClosure(Expression argsX) {
            if (argsX instanceof ClosureExpression) {
                return (ClosureExpression) argsX
            } else if (argsX instanceof ArgumentListExpression) {
                ArgumentListExpression listX = (ArgumentListExpression) argsX
                if (listX.expressions.size() == 1 && listX.getExpression(0) instanceof ClosureExpression) {
                    return (ClosureExpression)listX.getExpression(0)
                }
            }
            return null
        }

        private String buildPath(PropertyExpression propX) {
            String path = propX.propertyAsString
            Expression objX = propX.objectExpression
            while(objX instanceof PropertyExpression) {
                propX = ((PropertyExpression)objX)
                objX = propX.objectExpression
                path = "${propX.propertyAsString}.${path}"
            }
            if(objX instanceof VariableExpression) {
                VariableExpression varX = (VariableExpression)objX
                if(varX.accessedVariable.isDynamicTyped()) {
                    path = "${varX.name}.${path}"
                    path = "${nestedPath}${path}"
                    return path
                }
            }
            return null
        }
    }
}
