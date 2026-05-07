import java

from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


# tag::configSpec[]
@MicronautTest
class MyConfigurationPropertiesSpec:
    @Test
    def test_convert_date_from_map(self) -> None:
        # tag::runContext[]
        ctx = ApplicationContext.run({
            "spec.name": "MyConfigurationPropertiesSpec",
            "myapp.updatedAt": {  # <1>
                "day": 28,
                "month": 10,
                "year": 1982,
            },
        })
        # end::runContext[]

        try:
            MyConfigurationProperties = java.type(
                "micronaut.docs.config.converters.MyConfigurationProperties"
            )
            props = ctx.getBean(MyConfigurationProperties).asPolyglotValue()

            LocalDate = java.type("java.time.LocalDate")
            expected_date = LocalDate.of(1982, 10, 28)
            assert expected_date == props.get_updated_at()
        finally:
            ctx.close()
# end::configSpec[]
