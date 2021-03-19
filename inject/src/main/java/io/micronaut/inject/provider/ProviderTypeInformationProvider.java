package io.micronaut.inject.provider;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformationProvider;

import javax.inject.Provider;

/**
 * Makes {@link Argument#isWrapperType()} return true for Providers.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public final class ProviderTypeInformationProvider implements TypeInformationProvider {
    @Override
    public boolean isWrapperType(Class<?> type) {
        if (BeanProvider.class == type || Provider.class == type) {
            return true;
        } else if (JakartaProviderBeanDefinitionReference.isTypePresent()) {
            return jakarta.inject.Provider.class == type;
        }
        return false;
    }
}
