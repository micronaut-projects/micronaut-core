package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

/**
 * Implementation for {@link BeanProvider} bean lookups.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
final class BeanProviderDefinition extends AbstractProviderDefinition<BeanProvider<Object>> {
    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<BeanProvider<Object>> getBeanType() {
        return (Class) BeanProvider.class;
    }

    @Override
    protected BeanProvider<Object> buildProvider(
            BeanContext context,
            Argument<Object> argument,
            Qualifier<Object> qualifier,
            boolean singleton) {
        if (singleton) {

            return new BeanProvider<Object>() {
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
