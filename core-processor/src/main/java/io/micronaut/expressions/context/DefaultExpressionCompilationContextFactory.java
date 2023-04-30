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

import io.micronaut.context.annotation.AnnotationExpressionContext;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for producing expression evaluation context.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class DefaultExpressionCompilationContextFactory implements ExpressionCompilationContextFactory {

    private static final Collection<ClassElement> CONTEXT_TYPES = ConcurrentHashMap.newKeySet();
    private ExtensibleExpressionCompilationContext sharedContext;
    private final VisitorContext visitorContext;

    public DefaultExpressionCompilationContextFactory(VisitorContext visitorContext) {
        this.sharedContext = recreateContext();
        this.visitorContext = visitorContext;
    }

    @NotNull
    private DefaultExpressionCompilationContext recreateContext() {
        return new DefaultExpressionCompilationContext(CONTEXT_TYPES.toArray(ClassElement[]::new));
    }

    @Override
    @NonNull
    public ExpressionCompilationContext buildContextForMethod(@NonNull EvaluatedExpressionReference expression,
                                                              @NonNull MethodElement methodElement) {
        return buildForExpression(expression, null)
                 .extendWith(methodElement);
    }

    @Override
    @NonNull
    public ExpressionCompilationContext buildContext(EvaluatedExpressionReference expression, ClassElement thisElement) {
        return buildForExpression(expression, thisElement);
    }

    @Override
    public ExpressionCompilationContextFactory registerContextClass(ClassElement contextClass) {
        CONTEXT_TYPES.add(contextClass);
        this.sharedContext = recreateContext();
        return this;
    }

    private ExtensibleExpressionCompilationContext buildForExpression(EvaluatedExpressionReference expression, ClassElement thisElement) {
        String annotationName = expression.annotationName();
        String memberName = expression.annotationMember();

        ClassElement annotation = visitorContext.getClassElement(annotationName).orElse(null);

        ExtensibleExpressionCompilationContext evaluationContext = sharedContext;
        if (annotation != null) {
            evaluationContext = addAnnotationEvaluationContext(evaluationContext, annotation);
            evaluationContext = addAnnotationMemberEvaluationContext(evaluationContext, annotation, memberName);
        }

        if (thisElement != null) {
            return evaluationContext.withThis(thisElement);
        }
        return evaluationContext;
    }

    private ExtensibleExpressionCompilationContext addAnnotationEvaluationContext(
        ExtensibleExpressionCompilationContext currentEvaluationContext,
        ClassElement annotation) {

        return annotation.findAnnotation(AnnotationExpressionContext.class)
                   .flatMap(av -> av.annotationClassValue(AnnotationMetadata.VALUE_MEMBER))
                   .map(AnnotationClassValue::getName)
                   .flatMap(visitorContext::getClassElement)
                   .map(currentEvaluationContext::extendWith)
                   .orElse(currentEvaluationContext);
    }

    private ExtensibleExpressionCompilationContext addAnnotationMemberEvaluationContext(
        ExtensibleExpressionCompilationContext currentEvaluationContext,
        ClassElement annotation,
        String annotationMember) {

        ElementQuery<MethodElement> memberQuery =
            ElementQuery.ALL_METHODS
                .onlyDeclared()
                .annotated(am -> am.hasAnnotation(AnnotationExpressionContext.class))
                .named(annotationMember);

        return annotation.getEnclosedElements(memberQuery).stream()
                   .flatMap(element -> Optional.ofNullable(element.getDeclaredAnnotation(AnnotationExpressionContext.class)).stream())
                   .flatMap(av -> av.annotationClassValue(AnnotationMetadata.VALUE_MEMBER).stream())
                   .map(AnnotationClassValue::getName)
                   .flatMap(className -> visitorContext.getClassElement(className).stream())
                   .reduce(currentEvaluationContext, ExtensibleExpressionCompilationContext::extendWith, (a, b) -> a);
    }

    /**
     * cleanup any stored contexts.
     */
    @Internal
    public static void reset() {
        CONTEXT_TYPES.clear();
    }
}
