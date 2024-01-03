/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.annotation.processing.test

import io.micronaut.context.expressions.AbstractEvaluatedExpression
import io.micronaut.context.expressions.DefaultExpressionEvaluationContext
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.annotation.EvaluatedExpressionReferenceCounter
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import org.intellij.lang.annotations.Language

abstract class AbstractEvaluatedExpressionsSpec extends AbstractTypeElementSpec {

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new ContextRegistrar()]
    }

    List<Object> evaluateMultiple(String... expressions) {

        String classContent = ""
        for (int i = 0; i < expressions.size(); i++) {
            classContent += """

                @Value("${expressions[i]}")
                public Object field${i};

            """
        }

        def cls = """
            package test;
            import io.micronaut.context.annotation.Value;

            class Expr {
                ${classContent}
            }
        """.stripIndent().stripLeading()

        def applicationContext = buildContext(cls)
        def classLoader = applicationContext.classLoader

        def exprClassName = 'test.$Expr$Expr'
        def startingIndex = EvaluatedExpressionReferenceCounter.nextIndex(exprClassName) - expressions.length

        List<Object> result = new ArrayList<>()
        for (int i = startingIndex; i < startingIndex + expressions.size(); i++) {
            String exprFullName = exprClassName + i
            try {
                def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName).newInstance()
                result.add(exprClass.evaluate(new DefaultExpressionEvaluationContext(null, null, applicationContext, null)))
            } catch (ClassNotFoundException e) {
                return null
            }
        }

        return result
    }

    Object evaluate(String expression) {
        return evaluateAgainstContext(expression, "")
    }

    Object evaluateAgainstContext(String expression, @Language("java") String contextClass) {
        String exprFullName = 'test.$Expr$Expr';

        def cls = """
            package test;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Singleton;

            ${contextClass}

            @Singleton
            class Expr {
                @Value("${expression}")
                public Object field;
            }
        """.stripIndent().stripLeading()

        def applicationContext = buildContext(cls)
        def classLoader = applicationContext.classLoader

        try {
            def index = EvaluatedExpressionReferenceCounter.nextIndex(exprFullName)
            def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName + (index == 0 ? index : index - 1)).newInstance()
            exprClass.evaluate(new DefaultExpressionEvaluationContext(null, null, applicationContext, null))
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    Object evaluateMultipleAgainstContext(@Language("java") String contextClass, String... expressions) {

        String expr = ""
        for (int i = 0; i < expressions.length; i++) {
            expr += """

                @Value("${expressions[i]}")
                public Object field${i};

            """
        }

        def cls = """
            package test;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Singleton;

            ${contextClass}

            @Singleton
            class Expr {
                ${expr}
            }
        """.stripIndent().stripLeading()

        def applicationContext = buildContext(cls)
        def classLoader = applicationContext.classLoader

        def exprClassName = 'test.$Expr$Expr'
        def startingIndex = EvaluatedExpressionReferenceCounter.nextIndex(exprClassName) - expressions.length

        List<Object> result = new ArrayList<>()
        for (int i = startingIndex; i < startingIndex + expressions.size(); i++) {
            String exprFullName = exprClassName + i
            try {
                def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName).newInstance()
                result.add(exprClass.evaluate(new DefaultExpressionEvaluationContext(null, null, applicationContext,
                        null)))
            } catch (ClassNotFoundException e) {
                return null
            }
        }

        return result
    }

    Object evaluateSingle(String className,
                          @Language("java") String cls) {
        return evaluateSingle(className, cls, null)
    }

    Object evaluateSingle(String className,
                          @Language("java") String cls,
                          Object[] args) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def packageName = NameUtils.getPackageName(className)
        def exprClassName = (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + '$Expr'

        String exprFullName = "${packageName}.${exprClassName}"

        def applicationContext = buildContext(cls)
        def classLoader = applicationContext.classLoader

        try {
            def index = EvaluatedExpressionReferenceCounter.nextIndex(exprFullName)
            def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName + (index == 0 ? index : index - 1)).newInstance()
            return exprClass.evaluate(new DefaultExpressionEvaluationContext(null, args, applicationContext, null))
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    static class ContextRegistrar implements TypeElementVisitor<Object, Object> {
        static final List<String> CLASSES = ["test.Context"]

        static void setClasses(String...classes) {
            CLASSES.clear()
            CLASSES.addAll(classes)
        }

        static void reset() {
            CLASSES.clear()
            CLASSES.add("test.Context")
        }

        @Override
        void start(VisitorContext visitorContext) {
            for (cls in CLASSES) {
                visitorContext.getClassElement(cls).ifPresent {
                    visitorContext.expressionCompilationContextFactory.registerContextClass(it)
                }
            }
        }
    }
}
