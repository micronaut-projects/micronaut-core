package io.micronaut.inject.provider.exposedtypes;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.provider.AbstractInjectionPointBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The definition of a {@link Handle}, exposed as the handle, a {@link Lookup}, a {@link Source} and an
 * {@link AutoCloseable}, which has no type parameters.
 */
public final class HandleDefinition extends AbstractInjectionPointBeanDefinition.Disposable<Handle<Object>> {

    static final String NO_INJECTION_POINT = "<none>";

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Handle<Object>> getBeanType() {
        return (Class) Handle.class;
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        return Set.of(Handle.class, Lookup.class, Source.class, AutoCloseable.class);
    }

    @Override
    public List<Argument<?>> getTypeArguments(Class<?> type) {
        if (type == AutoCloseable.class) {
            return List.of();
        }
        return super.getTypeArguments(type);
    }

    @Override
    protected Handle<Object> build(BeanResolutionContext resolutionContext,
                                   BeanContext context,
                                   @Nullable InjectionPoint<?> injectionPoint) {
        if (injectionPoint instanceof ArgumentCoercible<?> coercible) {
            return new Handle<>(coercible.asArgument().getName());
        }
        return new Handle<>(NO_INJECTION_POINT);
    }
}
