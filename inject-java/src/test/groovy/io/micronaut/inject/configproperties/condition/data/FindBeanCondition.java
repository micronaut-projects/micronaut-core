package io.micronaut.inject.configproperties.condition.data;

import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;

import java.util.Objects;

public class FindBeanCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        Objects.requireNonNull(beanContext.getBean(Environment.class));
        DemoConfig demoConfig = beanContext.getBean(DemoConfig.class);
        return demoConfig.getName().equals("Acme");
    }
}
