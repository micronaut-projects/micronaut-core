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
package io.micronaut.context.env.groovy

import static org.codehaus.groovy.ast.tools.GeneralUtils.ASSIGN
import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.callThisX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX

import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.SourceUnit

/**
 * Transforms Groovy properties syntax into calls to setProperty(..) calculating the path
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class SetPropertyTransformer extends ClassCodeExpressionTransformer {

    final SourceUnit sourceUnit

    String nestedPath = ""
    String setPropertyMethodName = "setProperty"

    SetPropertyTransformer(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit
    }

    @Override
    Expression transform(Expression exp) {
        if (exp instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) exp
            if (be.operation.type == ASSIGN.type) {
                Expression left = be.leftExpression
                Expression right = be.rightExpression
                if (left instanceof VariableExpression) {
                    def varX = (VariableExpression) left
                    if (varX.accessedVariable.isDynamicTyped()) {
                        String path = "${nestedPath}${varX.name}"
                        return callThisX(setPropertyMethodName, args(constX(path), right))
                    }
                } else if (left instanceof PropertyExpression) {
                    PropertyExpression propX = (PropertyExpression) left
                    String path = buildPath(propX)
                    if (path != null) {
                        return callThisX(setPropertyMethodName, args(constX(path), right))
                    }
                }
            }
        } else if (exp instanceof MethodCallExpression) {
            MethodCallExpression methodX = (MethodCallExpression) exp
            Expression argsX = methodX.arguments
            ClosureExpression closureX = findSingleClosure(argsX)
            if (closureX != null) {
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
                return (ClosureExpression) listX.getExpression(0)
            }
        }
        return null
    }

    private String buildPath(PropertyExpression propX) {
        String path = propX.propertyAsString
        Expression objX = propX.objectExpression
        while (objX instanceof PropertyExpression) {
            propX = ((PropertyExpression) objX)
            objX = propX.objectExpression
            path = "${propX.propertyAsString}.${path}"
        }
        if (objX instanceof VariableExpression) {
            VariableExpression varX = (VariableExpression) objX
            if (varX.accessedVariable.isDynamicTyped()) {
                path = "${varX.name}.${path}"
                path = "${nestedPath}${path}"
                return path
            }
        }
        return null
    }
}
