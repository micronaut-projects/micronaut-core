package io.micronaut.inject.registration;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

/**
 * Resolves a definition of an implementation as an interface it implements, from Java and without a cast.
 */
public final class JavaCaller {

    private JavaCaller() {
    }

    @SuppressWarnings("rawtypes")
    public static Box resolveAsInterface(BeanContext context) {
        BeanDefinition<StringBox> definition = context.getBeanDefinition(StringBox.class);
        Argument<Box> type = Argument.of(Box.class, String.class);
        BeanRegistration<Box> registration = context.getBeanRegistration(definition, type);
        return registration.getBean();
    }
}
