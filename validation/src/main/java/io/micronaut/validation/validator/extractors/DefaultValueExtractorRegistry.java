package io.micronaut.validation.validator.extractors;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import javax.validation.valueextraction.ValueExtractor;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The default {@link ValueExtractorRegistry} that looks up from the {@link io.micronaut.context.BeanContext}.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
@Internal
class DefaultValueExtractorRegistry implements ValueExtractorRegistry {

    private final Set<Class> concreteExtractorTypes;
    private final BeanContext beanContext;

    /**
     * Default constructor.
     * @param beanContext The bean locator.
     */
    DefaultValueExtractorRegistry(@Nonnull BeanContext beanContext) {
        ArgumentUtils.requireNonNull("beanContext", beanContext);
        this.beanContext = beanContext;
        this.concreteExtractorTypes = beanContext.getBeanDefinitions(ValueExtractor.class)
                                           .stream()
                                           .filter(bd -> !bd.getTypeArguments(ValueExtractor.class).isEmpty())
                                           .map(bd -> bd.getTypeParameters(ValueExtractor.class)[0])
                                           .collect(Collectors.toSet());
    }

    @Nonnull
    @Override
    public <T> Optional<ValueExtractor<T>> findValueExtractor(@Nonnull Class<T> targetType) {
        ArgumentUtils.requireNonNull("targetType", targetType);
        final Class extractorType = concreteExtractorTypes.stream().filter(t -> t == targetType || t.isAssignableFrom(targetType)).findFirst().orElse(null);
        if (extractorType != null) {
            final Optional result = beanContext.findBean(ValueExtractor.class, Qualifiers.byTypeArguments(targetType));
            return result;
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> Optional<ValueExtractor<T>> findConcreteExtractor(@Nonnull Class<T> targetType) {
        ArgumentUtils.requireNonNull("targetType", targetType);
        if (concreteExtractorTypes.contains(targetType)) {
            final Optional result = beanContext.findBean(ValueExtractor.class, Qualifiers.byTypeArguments(targetType));
            return result;
        }
        return Optional.empty();
    }
}
