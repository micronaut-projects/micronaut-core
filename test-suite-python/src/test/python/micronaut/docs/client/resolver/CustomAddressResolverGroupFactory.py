# tag::class[]
import java
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Factory

AddressResolverGroup = java.type("io.netty.resolver.AddressResolverGroup")
DefaultAddressResolverGroup = java.type("io.netty.resolver.DefaultAddressResolverGroup")


@Factory
class CustomAddressResolverGroupFactory:

    @Singleton
    @Named("custom")  # <1>
    def customAddressResolverGroup(self) -> AddressResolverGroup:
        return DefaultAddressResolverGroup.INSTANCE  # <2>
# end::class[]
