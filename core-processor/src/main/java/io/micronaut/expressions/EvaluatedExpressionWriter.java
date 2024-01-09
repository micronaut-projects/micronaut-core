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
package io.micronaut.expressions;

import io.micronaut.context.expressions.AbstractEvaluatedExpression;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.expressions.context.ExpressionWithContext;
import io.micronaut.expressions.parser.CompoundEvaluatedExpressionParser;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.expressions.parser.exception.ExpressionParsingException;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

/**
 * Writer for compile-time expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class EvaluatedExpressionWriter extends AbstractClassFileWriter {
    private static final Method EVALUATED_EXPRESSIONS_CONSTRUCTOR =
        new Method(CONSTRUCTOR_NAME, getConstructorDescriptor(Object.class));

    private static final Type EVALUATED_EXPRESSION_TYPE =
        Type.getType(AbstractEvaluatedExpression.class);

    private final ExpressionWithContext expressionMetadata;
    private final VisitorContext visitorContext;
    private final Element originatingElement;

    private static final Set<String> WRITTEN_CLASSES = new HashSet<>();

    public EvaluatedExpressionWriter(ExpressionWithContext expressionMetadata,
                                     VisitorContext visitorContext,
                                     Element originatingElement) {
        this.visitorContext = visitorContext;
        this.expressionMetadata = expressionMetadata;
        this.originatingElement = originatingElement;
    }

    @Override
    public void accept(ClassWriterOutputVisitor outputVisitor) throws IOException {
        String expressionClassName = expressionMetadata.expressionClassName();
        if (WRITTEN_CLASSES.contains(expressionClassName)) {
            return;
        }
        try (OutputStream outputStream = outputVisitor.visitClass(expressionClassName,
            getOriginatingElements())) {
            ClassWriter classWriter = generateClassBytes(expressionClassName);
            outputStream.write(classWriter.toByteArray());
            WRITTEN_CLASSES.add(expressionClassName);
        }
    }

    private ClassWriter generateClassBytes(String expressionClassName) {
        ClassWriter classWriter = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);

        startPublicClass(
            classWriter,
            getInternalName(expressionClassName),
            EVALUATED_EXPRESSION_TYPE);

        GeneratorAdapter cv = startConstructor(classWriter, Object.class);
        cv.loadThis();
        cv.loadArg(0);

        cv.invokeConstructor(EVALUATED_EXPRESSION_TYPE, EVALUATED_EXPRESSIONS_CONSTRUCTOR);
        // RETURN
        cv.returnValue();
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);

        GeneratorAdapter evaluateMethodVisitor = startProtectedMethod(classWriter, "doEvaluate",
            Object.class.getName(), ExpressionEvaluationContext.class.getName());

        ExpressionCompilationContext ctx = new ExpressionCompilationContext(
            new ExpressionVisitorContext(expressionMetadata.evaluationContext(), visitorContext),
            evaluateMethodVisitor);

        Object annotationValue = expressionMetadata.annotationValue();

        try {
            ExpressionNode ast = new CompoundEvaluatedExpressionParser(annotationValue).parse();
            ast.compile(ctx);
            pushBoxPrimitiveIfNecessary(ast.resolveType(ctx), evaluateMethodVisitor);
        } catch (ExpressionParsingException | ExpressionCompilationException ex) {
            failCompilation(ex, annotationValue);
        }

        evaluateMethodVisitor.visitMaxs(2, 3);
        evaluateMethodVisitor.returnValue();
        return classWriter;
    }

    private void failCompilation(Throwable ex, Object initialAnnotationValue) {
        String strRepresentation = null;

        if (initialAnnotationValue instanceof String str) {
            strRepresentation = str;
        } else if (initialAnnotationValue instanceof String[] strArray) {
            strRepresentation = Arrays.toString(strArray);
        }

        String message = null;
        if (ex instanceof ExpressionParsingException parsingException) {
            message = "Failed to parse evaluated expression [" + strRepresentation + "]. " +
                          "Cause: " + parsingException.getMessage();
        } else if (ex instanceof ExpressionCompilationException compilationException) {
            message = "Failed to compile evaluated expression [" + strRepresentation + "]. " +
                          "Cause: " + compilationException.getMessage();
        }

        visitorContext.fail(message, originatingElement);
    }
}
