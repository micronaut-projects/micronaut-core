package io.micronaut.expressions.context;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.Closeable;
import java.io.IOException;

/**
 * Factory interface for producing expression evaluation context.
 */
@Experimental
public interface ExpressionCompilationContextFactory {
    /**
     * Builds expression evaluation context for method. Expression evaluation context
     * for method allows referencing method parameter names in evaluated expressions.
     *
     * @param expression    expression reference
     * @param methodElement annotated method
     * @return evaluation context for method
     */
    @NonNull
    ExpressionCompilationContext buildContextForMethod(@NonNull EvaluatedExpressionReference expression,
                                                       @NonNull MethodElement methodElement);

    /**
     * Builds expression evaluation context for expression reference.
     *
     * @param expression expression reference
     * @return evaluation context for method
     */
    @NonNull
    ExpressionCompilationContext buildContext(EvaluatedExpressionReference expression);

    /**
     * Adds evaluated expression context class element to context loader
     * at compilation time.
     *
     * <p>This method should be invoked from the {@link io.micronaut.inject.visitor.TypeElementVisitor#start(VisitorContext)} of a {@link io.micronaut.inject.visitor.TypeElementVisitor}</p>
     *
     * @param contextClass context class element
     */
    ExpressionCompilationContextFactory registerContextClass(@NonNull ClassElement contextClass);

}
