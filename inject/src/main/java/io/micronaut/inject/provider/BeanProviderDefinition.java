package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.AnyQualifier;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.stream.Stream;

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
        return new BeanProvider<Object>() {
            @Override
            public Object get() {
                return context.getBean(argument, qualifier);
            }

            @Override
            public boolean isUnique() {
                try {
                    context.getBeanDefinition(argument, qualifier instanceof AnyQualifier ? null : qualifier);
                    return true;
                } catch (NoSuchBeanException e) {
                    return false;
                }
            }

            @Override
            public boolean isPresent() {
                return context.containsBean(argument, qualifier);
            }

            @Override
            public boolean isResolvable() {
                return isPresent() && (isUnique() || qualifier instanceof AnyQualifier);
            }

            @NotNull
            @Override
            public Iterator<Object> iterator() {
                return context.getBeansOfType(argument, qualifier).iterator();
            }

            @Override
            public Stream<Object> stream() {
                return context.streamOfType(argument, qualifier);
            }
        };
    }
}
