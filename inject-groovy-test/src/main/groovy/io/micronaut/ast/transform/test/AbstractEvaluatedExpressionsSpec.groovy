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
package io.micronaut.ast.transform.test

import io.micronaut.context.AbstractEvaluatedExpression
import io.micronaut.core.expression.EvaluatedExpression
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.annotation.EvaluatedExpressionReference
import org.intellij.lang.annotations.Language

class AbstractEvaluatedExpressionsSpec extends AbstractBeanDefinitionSpec {

    List<Object> evaluateMultiple(String... expressions) {

        String classContent = ""
        for (int i = 0; i < expressions.size(); i++) {
            classContent += """

                @Value("${expressions[i]}")
                Object field${i}

            """
        }

        def cls = """
            package test
            import io.micronaut.context.annotation.Value
            import io.micronaut.context.annotation.EvaluatedExpressionContext

            class Expr {
                ${classContent}
            }
        """.stripIndent().stripLeading()

        def applicationContext = buildContext(cls)
        def classLoader = applicationContext.classLoader

        def exprClassName = 'test.$Expr$Expr'
        def startingIndex = EvaluatedExpressionReference.nextIndex(exprClassName) - expressions.length

        List<Object> result = new ArrayList<>()
        for (int i = startingIndex; i < startingIndex + expressions.size(); i++) {
            String exprFullName = exprClassName + i
            try {
                def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName).newInstance()
                exprClass.configure(applicationContext)
                result.add(exprClass.evaluate())
            } catch (ClassNotFoundException e) {
                return null
            }
        }

        return result
    }

    Object evaluate(String expression) {
        return evaluateAgainstContext(expression, "")
    }

    Object evaluateAgainstContext(String expression, @Language("groovy") String contextClass) {
        String exprClassName = 'test.$Expr$Expr';

        def cls = """
            package test
            import io.micronaut.context.annotation.Value
            import io.micronaut.context.annotation.EvaluatedExpressionContext

            ${contextClass}

            class Expr {
                @Value("${expression}")
                Object field
            }
        """.stripIndent().stripLeading()

        def applicationContext = buildContext(cls)
        def classLoader = applicationContext.classLoader

        try {
            def index = EvaluatedExpressionReference.nextIndex(exprClassName)
            def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprClassName + (index == 0 ? index : index - 1)).newInstance()
            exprClass.configure(applicationContext)
            return exprClass.evaluate();
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    EvaluatedExpression buildSingleExpressionFromClass(String className,
                                                       @Language("groovy") String cls) {

        def classSimpleName = NameUtils.getSimpleName(className)
        def packageName = NameUtils.getPackageName(className)
        def exprClassName = (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + '$Expr'

        String exprFullName = "${packageName}.${exprClassName}"

        def applicationContext = buildContext(cls)
        def classLoader = applicationContext.classLoader

        try {
            def index = EvaluatedExpressionReference.nextIndex(exprFullName)
            def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName + (index == 0 ? index : index - 1)).newInstance()
            exprClass.configure(applicationContext)
            return exprClass
        } catch (ClassNotFoundException e) {
            return null
        }
    }
}
