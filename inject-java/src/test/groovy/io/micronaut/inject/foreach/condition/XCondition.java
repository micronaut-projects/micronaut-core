package io.micronaut.inject.foreach.condition;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.QualifiedBeanType;

public class XCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        if (beanContext instanceof ApplicationContext appCtx &&
            context.getComponent() instanceof QualifiedBeanType<?> qualifiedBeanType) {
            Qualifier<?> declaredQualifier = qualifiedBeanType.getDeclaredQualifier();
            if (declaredQualifier instanceof Named named) {
                return appCtx.getProperty(
                    "foo." + named.getName() + ".enabled",
                    Boolean.class
                ).orElse(true);
            }
        }
        return true;
    }
}
