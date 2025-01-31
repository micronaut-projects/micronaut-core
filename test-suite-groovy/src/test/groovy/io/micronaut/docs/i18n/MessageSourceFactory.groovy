package io.micronaut.docs.i18n

//tag::clazz[]
import io.micronaut.context.MessageSource
import io.micronaut.context.annotation.Factory
import io.micronaut.context.i18n.ResourceBundleMessageSource
import io.micronaut.core.order.Ordered
import jakarta.inject.Singleton

@Factory
class MessageSourceFactory {
    @Singleton
    MessageSource createMessageSource() {
        new ResourceBundleMessageSource("io.micronaut.docs.i18n.messages", Ordered.HIGHEST_PRECEDENCE)
    }
}
//end::clazz[]
