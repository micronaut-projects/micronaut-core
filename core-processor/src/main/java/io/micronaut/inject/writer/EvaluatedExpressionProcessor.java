/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.BuildTimeInit;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.expressions.EvaluatedExpressionWriter;
import io.micronaut.expressions.context.DefaultExpressionCompilationContextFactory;
import io.micronaut.expressions.context.ExpressionEvaluationContext;
import io.micronaut.expressions.context.ExpressionWithContext;
import io.micronaut.expressions.util.EvaluatedExpressionsUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Internal utility class for writing annotation metadata with evaluated expressions.
 */
@Internal
public final class EvaluatedExpressionProcessor {
    private final Collection<ExpressionWithContext> evaluatedExpressions = new ArrayList<>(2);
    private final DefaultExpressionCompilationContextFactory expressionCompilationContextFactory;
    private final VisitorContext visitorContext;
    private final Element originatingElement;

    /**
     * Default constructor.
     * @param visitorContext The visitor context
     * @param originatingElement The originating element
     */
    public EvaluatedExpressionProcessor(
        VisitorContext visitorContext,
        Element originatingElement) {
        this.visitorContext = visitorContext;
        this.expressionCompilationContextFactory = new DefaultExpressionCompilationContextFactory(visitorContext);
        this.originatingElement = originatingElement;
    }

    /**
     * Reset after processing.
     */
    public static void reset() {
        DefaultExpressionCompilationContextFactory.reset();
    }

    /**
     * Process evaluated expression contained within annotation metadata.
     * @param annotationMetadata The annotation metadata
     * @param thisElement If the expressino is evaluated in a non-static context, this type represents {@code this}
     */
    public void processEvaluatedExpressions(AnnotationMetadata annotationMetadata, @Nullable ClassElement thisElement) {
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            annotationMetadata = annotationMetadata.getDeclaredMetadata();
        }

        Collection<EvaluatedExpressionReference> expressionReferences =
            EvaluatedExpressionsUtils.findEvaluatedExpressionReferences(annotationMetadata);

        expressionReferences.stream()
            .map(expressionReference -> {
                ExpressionEvaluationContext evaluationContext = expressionCompilationContextFactory.buildContext(expressionReference, thisElement);
                return new ExpressionWithContext(expressionReference, evaluationContext);
            })
            .forEach(this::addExpression);
    }

    public void processEvaluatedExpressions(MethodElement methodElement) {
        Collection<EvaluatedExpressionReference> expressionReferences =
            EvaluatedExpressionsUtils.findEvaluatedExpressionReferences(methodElement.getDeclaredMetadata());

        expressionReferences.stream()
            .map(expression -> {
                ExpressionEvaluationContext evaluationContext = expressionCompilationContextFactory.buildContextForMethod(expression, methodElement);
                return new ExpressionWithContext(expression, evaluationContext);
            })
            .forEach(this::addExpression);

        ClassElement resolvedThis = methodElement.isStatic() || methodElement instanceof ConstructorElement ? null : methodElement.getOwningType();
        for (ParameterElement parameter: methodElement.getParameters()) {
            processEvaluatedExpressions(parameter.getAnnotationMetadata(), resolvedThis);
        }
    }

    private void addExpression(ExpressionWithContext ee) {
        if (!evaluatedExpressions.contains(ee)) {
            evaluatedExpressions.add(ee);
        }
    }

    public Collection<ExpressionWithContext> getEvaluatedExpressions() {
        return evaluatedExpressions;
    }

    public void writeEvaluatedExpressions(ClassWriterOutputVisitor visitor) throws IOException {
        for (ExpressionWithContext expressionMetadata: getEvaluatedExpressions()) {
            EvaluatedExpressionWriter expressionWriter = new EvaluatedExpressionWriter(
                expressionMetadata,
                visitorContext,
                originatingElement
            );

            expressionWriter.accept(visitor);
        }
    }

    public boolean hasEvaluatedExpressions() {
        return !this.evaluatedExpressions.isEmpty();
    }

    public void registerExpressionForBuildTimeInit(ClassDef.ClassDefBuilder classDefBuilder) {
        String[] expressionClassNames = getEvaluatedExpressions()
            .stream().map(ExpressionWithContext::expressionClassName).toArray(String[]::new);
        if (ArrayUtils.isNotEmpty(expressionClassNames)) {
            classDefBuilder.addAnnotation(
                AnnotationDef.builder(BuildTimeInit.class)
                    .addMember("value", expressionClassNames)
                    .build()
            );
        }
    }
}
