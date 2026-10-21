package io.micronaut.inject.provider.lookuppoint;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.provider.AbstractInjectionPointBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class LookupDefinition extends AbstractInjectionPointBeanDefinition<Lookup<Object>> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Lookup<Object>> getBeanType() {
        return (Class) Lookup.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Lookup<Object> build(BeanResolutionContext resolutionContext,
                                   BeanContext context,
                                   @Nullable InjectionPoint<?> injectionPoint) {
        BeanResolutionContext.Segment<?, ?> segment = resolutionContext.getPath().currentSegment().orElseThrow();
        Argument<Object> type = (Argument<Object>) ((ArgumentCoercible<?>) Objects.requireNonNull(injectionPoint))
            .asArgument().getFirstTypeVariable().orElseThrow();
        Qualifier<Object> qualifier = (Qualifier<Object>) resolutionContext.getCurrentQualifier();
        BeanDefinition<Object> definition = context.getBeanDefinition(type, qualifier);
        BeanDependencyResolver resolver = Objects.requireNonNull(resolutionContext.getDependencyResolver());
        BeanRegistration<Object> eager = resolutionContext.getBeanRegistration(definition, type, new RenamedSegment<>(segment, "eager"));
        return new Lookup<>(resolver, new RenamedSegment<>(segment, "later"), definition, type, eager);
    }
}
