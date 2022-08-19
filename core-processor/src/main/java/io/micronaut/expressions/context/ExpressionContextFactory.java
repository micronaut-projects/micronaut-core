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
package io.micronaut.expressions.context;

import io.micronaut.context.annotation.EvaluatedExpressionContext;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.EvaluatedExpressionReference;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Optional;

/**
 * Factory for producing expression evaluation context.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class ExpressionContextFactory {
    private final ExpressionEvaluationContext loadedExpressionEvaluationContext;
    private final VisitorContext visitorContext;

    public ExpressionContextFactory(VisitorContext visitorContext) {
        this.loadedExpressionEvaluationContext = ExpressionContextLoader.getExpressionContext();
        this.visitorContext = visitorContext;
    }

    /**
     * Builds expression evaluation context for method. Expression evaluation context
     * for method allows referencing method parameter names in evaluated expressions.
     *
     * @param expression expression reference
     * @param methodElement annotated method
     * @return evaluation context for method
     */
    @NonNull
    public ExpressionEvaluationContext buildForMethod(@NonNull EvaluatedExpressionReference expression,
                                                      @NonNull MethodElement methodElement) {
        ExpressionEvaluationContext evaluationContext = buildEvaluationContext(expression);
        return new CompositeExpressionEvaluationContext(
            evaluationContext,
            new MethodExpressionEvaluationContext(methodElement));
    }

    /**
     * Builds expression evaluation context for expression reference.
     *
     * @param expression expression reference
     * @return evaluation context for method
     */
    @NonNull
    public ExpressionEvaluationContext buildEvaluationContext(EvaluatedExpressionReference expression) {
        String annotationName = expression.annotationName();
        String memberName = expression.annotationMember();

        ClassElement annotation = visitorContext.getClassElement(annotationName).orElse(null);

        ExpressionEvaluationContext evaluationContext = loadedExpressionEvaluationContext;
        if (annotation != null) {
            evaluationContext = addAnnotationLevelEvaluationContext(evaluationContext, annotation);
            evaluationContext = addAnnotationMemberEvaluationContext(evaluationContext, annotation, memberName);
        }

        return evaluationContext;
    }

    private ExpressionEvaluationContext addAnnotationLevelEvaluationContext(
        ExpressionEvaluationContext currentEvaluationContext,
        ClassElement annotation) {

        ExpressionEvaluationContext annotationLevelContext =
            Optional.ofNullable(annotation.getAnnotation(EvaluatedExpressionContext.class))
                .flatMap(av -> av.annotationClassValue(AnnotationMetadata.VALUE_MEMBER))
                .map(AnnotationClassValue::getName)
                .flatMap(visitorContext::getClassElement)
                .map(BeanContextExpressionEvaluationContext::new)
                .orElse(null);

        if (annotationLevelContext != null) {
            return new CompositeExpressionEvaluationContext(
                currentEvaluationContext,
                annotationLevelContext);
        }

        return currentEvaluationContext;
    }

    private ExpressionEvaluationContext addAnnotationMemberEvaluationContext(
        ExpressionEvaluationContext currentEvaluationContext,
        ClassElement annotation,
        String annotationMember) {
        ElementQuery<MethodElement> memberQuery =
            ElementQuery.ALL_METHODS
                .onlyDeclared()
                .annotated(am -> am.hasAnnotation(EvaluatedExpressionContext.class))
                .named(annotationMember);

        ExpressionEvaluationContext annMemberContext =
            annotation.getEnclosedElements(memberQuery).stream()
                .map(element -> Optional.ofNullable(element.getDeclaredAnnotation(EvaluatedExpressionContext.class)))
                .flatMap(Optional::stream)
                .flatMap(av -> av.annotationClassValue(AnnotationMetadata.VALUE_MEMBER).stream())
                .map(AnnotationClassValue::getName)
                .flatMap(className -> visitorContext.getClassElement(className).stream())
                .findFirst()
                .map(BeanContextExpressionEvaluationContext::new)
                .orElse(null);

        if (annMemberContext != null) {
            return new CompositeExpressionEvaluationContext(
                currentEvaluationContext,
                annMemberContext);
        }

        return currentEvaluationContext;
    }
}
