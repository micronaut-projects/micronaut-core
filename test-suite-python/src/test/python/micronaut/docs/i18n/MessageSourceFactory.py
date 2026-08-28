import java

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory, Requires
# end::imports[]

MessageSource = java.type("io.micronaut.context.MessageSource")
Ordered = java.type("io.micronaut.core.order.Ordered")
ResourceBundleMessageSource = java.type("io.micronaut.context.i18n.ResourceBundleMessageSource")


@Requires(property="spec.name", value="I18nSpec")
# tag::clazz[]
@Factory
class MessageSourceFactory:
    @Singleton
    def createMessageSource(self) -> MessageSource:
        return ResourceBundleMessageSource("io.micronaut.docs.i18n.messages", Ordered.HIGHEST_PRECEDENCE)
# end::clazz[]
