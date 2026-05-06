from java.time import LocalDate

from micronaut.context.annotation import ConfigurationProperties


# tag::class[]
@ConfigurationProperties("myapp")
class MyConfigurationProperties:
    PREFIX: str = "myapp"

    updatedAt: LocalDate = None

    def get_updated_at(self) -> LocalDate:
        return self.updatedAt
# end::class[]
