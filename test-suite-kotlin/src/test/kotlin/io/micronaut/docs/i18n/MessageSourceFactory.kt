package io.micronaut.docs.i18n

//tag::imports[]
import io.micronaut.context.MessageSource
import io.micronaut.context.annotation.Factory
import io.micronaut.context.i18n.ResourceBundleMessageSource
import io.micronaut.core.order.Ordered
import jakarta.inject.Singleton
//end::imports[]

//tag::clazz[]
@Factory
internal class MessageSourceFactory {
    @Singleton
    fun createMessageSource(): MessageSource =
        ResourceBundleMessageSource("io.micronaut.docs.i18n.messages", Ordered.HIGHEST_PRECEDENCE)
}
//end::clazz[]
