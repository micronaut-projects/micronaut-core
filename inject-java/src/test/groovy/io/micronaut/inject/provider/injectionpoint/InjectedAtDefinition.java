package io.micronaut.inject.provider.injectionpoint;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.provider.AbstractInjectionPointBeanDefinition;
import org.jspecify.annotations.Nullable;

/**
 * A bean built from the injection point it is injected into: it holds the name of the field or argument, and its
 * type argument.
 */
public final class InjectedAtDefinition extends AbstractInjectionPointBeanDefinition<InjectedAt<Object>> {

    static final String NO_INJECTION_POINT = "<none>";

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<InjectedAt<Object>> getBeanType() {
        return (Class) InjectedAt.class;
    }

    @Override
    protected InjectedAt<Object> build(BeanResolutionContext resolutionContext,
                                       BeanContext context,
                                       @Nullable InjectionPoint<?> injectionPoint) {
        if (injectionPoint instanceof ArgumentCoercible<?> coercible) {
            Argument<?> argument = coercible.asArgument();
            return new InjectedAt<>(argument.getName(), argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
        }
        return new InjectedAt<>(NO_INJECTION_POINT, Argument.OBJECT_ARGUMENT);
    }
}
