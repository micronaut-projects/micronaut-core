package io.micronaut.runtime.context.env;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.ReturnType;

import javax.inject.Singleton;

/**
 * Internal interceptor that .
 *
 * @author graemerocher
 * @since 1.3.0
 */
@Singleton
@Internal
class ConfigurationIntroductionAdvice implements MethodInterceptor<Object, Object> {
    private final Environment environment;
    private final BeanLocator beanLocator;

    /**
     * Default constructor.
     * @param environment The environment
     * @param beanLocator  The bean locator
     */
    ConfigurationIntroductionAdvice(Environment environment, BeanLocator beanLocator) {
        this.environment = environment;
        this.beanLocator = beanLocator;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final ReturnType<Object> rt = context.getReturnType();
        final Class<Object> returnType = rt.getType();
        if (context.isTrue(ConfigurationAdvice.class, "bean")) {
            if (context.isNullable()) {
                final Object v = beanLocator.findBean(returnType).orElse(null);
                if (v != null) {
                    return environment.convertRequired(v, returnType);
                } else {
                    return v;
                }
            } else {
                return environment.convertRequired(
                        beanLocator.getBean(returnType),
                        returnType
                );
            }
        } else {
            final String property = context.stringValue(ConfigurationAdvice.class).orElse(null);
            if (property == null) {
                throw new IllegalStateException("No property name available to resolve");
            }
            if (context.isNullable()) {
                return environment.getProperty(
                        property,
                        returnType
                ).orElse(null);
            } else {
                return environment.getRequiredProperty(
                        property,
                        returnType
                );
            }
        }
    }
}
