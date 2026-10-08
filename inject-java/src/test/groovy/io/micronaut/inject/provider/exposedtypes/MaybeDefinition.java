package io.micronaut.inject.provider.exposedtypes;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.provider.AbstractInjectionPointBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The definition of a {@link Maybe}, declared nullable: it builds nothing for an injection point named
 * {@code absent}.
 */
public final class MaybeDefinition extends AbstractInjectionPointBeanDefinition<Maybe<Object>> {

    public MaybeDefinition() {
        super(nullable());
    }

    private static MutableAnnotationMetadata nullable() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, Map.of());
        return metadata;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Maybe<Object>> getBeanType() {
        return (Class) Maybe.class;
    }

    @Override
    @SuppressWarnings("NullAway")
    protected Maybe<Object> build(BeanResolutionContext resolutionContext,
                                  BeanContext context,
                                  @Nullable InjectionPoint<?> injectionPoint) {
        if (injectionPoint instanceof ArgumentCoercible<?> coercible) {
            String name = coercible.asArgument().getName();
            return name.equals("absent") ? null : new Maybe<>(name);
        }
        return null;
    }
}
