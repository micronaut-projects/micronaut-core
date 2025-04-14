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
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.expressions.context.ExpressionWithContext;
import io.micronaut.expressions.parser.CompoundEvaluatedExpressionParser;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.expressions.parser.exception.ExpressionParsingException;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.inject.writer.ClassOutputWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writer for compile-time expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class EvaluatedExpressionWriter implements ClassOutputWriter {

    private static final Method DO_EVALUATE_METHOD
        = ReflectionUtils.getRequiredMethod(AbstractEvaluatedExpression.class, "doEvaluate", ExpressionEvaluationContext.class);

    private static final Set<String> WRITTEN_CLASSES = new HashSet<>();

    private final ExpressionWithContext expressionMetadata;
    private final VisitorContext visitorContext;
    private final Element originatingElement;

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
        try (OutputStream outputStream = outputVisitor.visitClass(expressionClassName, originatingElement)) {
            ClassDef objectDef = generateClassDef(expressionClassName);
            outputStream.write(
                ByteCodeWriterUtils.writeByteCode(
                    objectDef,
                    visitorContext
                )
            );
            WRITTEN_CLASSES.add(expressionClassName);
        }
    }

    private ClassDef generateClassDef(String expressionClassName) {
        return ClassDef.builder(expressionClassName)
            .synthetic()
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Generated.class)
            .superclass(ClassTypeDef.of(AbstractEvaluatedExpression.class))
            .addMethod(MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Object.class)
                .build((aThis, methodParameters) ->
                    aThis.superRef().invokeConstructor(methodParameters.get(0)))
            )
            .addMethod(MethodDef.override(DO_EVALUATE_METHOD)
                .build((aThis, methodParameters) -> {

                    List<StatementDef> statements = new ArrayList<>();

                    ExpressionCompilationContext ctx = new ExpressionCompilationContext(
                        new ExpressionVisitorContext(expressionMetadata.evaluationContext(), visitorContext),
                        methodParameters.get(0),
                        statements
                    );

                    Object annotationValue = expressionMetadata.annotationValue();

                    try {
                        statements.add(
                            new CompoundEvaluatedExpressionParser(annotationValue)
                                .parse()
                                .compile(ctx)
                                .returning()
                        );
                    } catch (ExpressionParsingException | ExpressionCompilationException ex) {
                        throw failCompilation(ex, annotationValue);
                    }
                    return StatementDef.multi(statements);
                }))

            .build();
    }

    private ProcessingException failCompilation(Throwable ex, Object initialAnnotationValue) {
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
        return new ProcessingException(originatingElement, message, ex);
    }

}
