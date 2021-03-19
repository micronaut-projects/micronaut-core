package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import jakarta.inject.Provider;

/**
 * Implementation for Jakarta bean lookups.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
final class JakartaProviderBeanDefinition extends AbstractProviderDefinition<Provider<Object>> {
    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return JakartaProviderBeanDefinitionReference.isTypePresent();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Provider<Object>> getBeanType() {
        return (Class) Provider.class;
    }

    @Override
    protected Provider<Object> buildProvider(BeanContext context, Argument<Object> argument, Qualifier<Object> qualifier, boolean singleton) {
        if (singleton) {

            return new Provider<Object>() {
                Object bean;
                @Override
                public Object get() {
                    if (bean == null) {
                        bean = context.getBean(argument, qualifier);
                    }
                    return bean;
                }
            };
        } else {
            return () -> context.getBean(argument, qualifier);
        }
    }
}
