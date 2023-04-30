package io.micronaut.docs.expressions;

import io.micronaut.expressions.context.ExpressionEvaluationContextRegistrar;

public class ContextRegistrar implements ExpressionEvaluationContextRegistrar {
    @Override
    public String getContextClassName() {
        return "io.micronaut.docs.expressions.CustomEvaluationContext";
    }
}
