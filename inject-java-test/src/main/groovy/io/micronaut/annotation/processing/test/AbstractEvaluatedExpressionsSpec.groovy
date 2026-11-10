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
import io.micronaut.core.expressions.EvaluatedExpressionReference
import io.micronaut.core.naming.NameUtils
import io.micronaut.expressions.util.EvaluatedExpressionsUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import org.intellij.lang.annotations.Language


abstract class AbstractEvaluatedExpressionsSpec extends AbstractTypeElementSpec {

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new ContextRegistrar(), new ExpressionTypeCollector()]
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

        List<Object> result = new ArrayList<>()
        for (int i = 0; i < expressions.size(); i++) {
            String exprFullName = expressionClassName(classLoader, 'test.Expr', 'field$field' + i)
            if (exprFullName == null) {
                return null
            }
            def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName).newInstance()
            result.add(exprClass.evaluate(new DefaultExpressionEvaluationContext(null, null, applicationContext, null)))
        }

        return result
    }

    Object evaluate(String expression) {
        return evaluateAgainstContext(expression, "")
    }

    Object evaluateAgainstContext(String expression, @Language("java") String contextClass) {

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

        String exprFullName = expressionClassName(classLoader, 'test.Expr', 'field$field')
        if (exprFullName == null) {
            return null
        }
        def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName).newInstance()
        exprClass.evaluate(new DefaultExpressionEvaluationContext(null, null, applicationContext, null))
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

        List<Object> result = new ArrayList<>()
        for (int i = 0; i < expressions.size(); i++) {
            String exprFullName = expressionClassName(classLoader, 'test.Expr', 'field$field' + i)
            if (exprFullName == null) {
                return null
            }
            def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName).newInstance()
            result.add(exprClass.evaluate(new DefaultExpressionEvaluationContext(null, null, applicationContext,
                    null)))
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
        def applicationContext = buildContext(cls)
        def classLoader = applicationContext.classLoader

        String exprFullName = expressionClassName(classLoader, className, null)
        if (exprFullName == null) {
            return null
        }
        def exprClass = (AbstractEvaluatedExpression) classLoader.loadClass(exprFullName).newInstance()
        return exprClass.evaluate(new DefaultExpressionEvaluationContext(null, args, applicationContext, null))
    }

    /**
     * The name of an expression class the compilation wrote for a class, the last written when several match.
     *
     * @param classLoader The loader of the compilation's classes
     * @param className The name of the class declaring the annotated element
     * @param element The description of the annotated element the name starts with, such as {@code field$name}, or null for any
     * @return The name of the expression class, or null
     */
    protected static String expressionClassName(ClassLoader classLoader, String className, String element) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def packageName = NameUtils.getPackageName(className)
        String prefix = packageName + '.' + (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + '$Expr$' + (element == null ? '' : element + '$')
        List<String> names = ((JavaFileObjectClassLoader) classLoader).classNames.findAll { it.startsWith(prefix) }
        return names ? names.last() : null
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

    static class ExpressionTypeCollector implements TypeElementVisitor<Object, Object> {

        static final Map<String, ClassElement> TYPES = [:]

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            def annotation = element.getAnnotation("exp.ExpAnnotation")
            if (annotation != null) {
                EvaluatedExpressionReference reference = annotation.getValues().get("value") as EvaluatedExpressionReference;
                TYPES.put(
                        reference.annotationValue() as String,
                        EvaluatedExpressionsUtils.evaluateExpressionType(context, element, reference)
                )
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            def annotation = element.getAnnotation("exp.ExpAnnotation")
            if (annotation != null) {
                EvaluatedExpressionReference reference = annotation.getValues().get("value") as EvaluatedExpressionReference;
                TYPES.put(
                        reference.annotationValue() as String,
                        EvaluatedExpressionsUtils.evaluateExpressionType(context, element, reference)
                )
            }
        }
    }
}
