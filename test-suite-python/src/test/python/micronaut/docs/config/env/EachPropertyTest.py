import java

from micronaut.context import ApplicationContext
from micronaut.context.env import PropertySource
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test


@MicronautTest
class EachPropertyTest:
    @Test
    @Disabled("Python @EachProperty named configuration beans are not returned as expected yet")
    def test_each_property(self) -> None:
        # tag::config[]
        application_context = ApplicationContext.run(PropertySource.of(
            "test",
            {
                "test.datasource.one.url": "jdbc:mysql://localhost/one",
                "test.datasource.two.url": "jdbc:mysql://localhost/two",
            },
        ))
        # end::config[]

        try:
            # tag::beans[]
            DataSourceConfiguration = java.type(
                "micronaut.docs.config.env.DataSourceConfiguration"
            )
            beans_of_type = application_context.getBeansOfType(DataSourceConfiguration)
            assert 2 == beans_of_type.size()  # <1>

            first_config = application_context.getBean(
                DataSourceConfiguration,
                Qualifiers.byName("one"),  # <2>
            ).asPolyglotValue()

            URI = java.type("java.net.URI")
            assert URI("jdbc:mysql://localhost/one") == first_config.get_url()
            # end::beans[]
        finally:
            application_context.close()

    @Test
    @Disabled("Python list-based @EachProperty @Parameter index passes an unexpected arity to GraalPy")
    def test_each_property_list(self) -> None:
        application_context = ApplicationContext.run({
            "ratelimits": [
                {"period": "10s", "limit": "1000"},
                {"period": "1m", "limit": "5000"},
            ],
        })

        try:
            RateLimitsConfiguration = java.type(
                "micronaut.docs.config.env.RateLimitsConfiguration"
            )
            beans_of_type = application_context.streamOfType(RateLimitsConfiguration).toList()

            assert 2 == beans_of_type.size()
            assert 1000 == beans_of_type.get(0).asPolyglotValue().get_limit()
            assert 5000 == beans_of_type.get(1).asPolyglotValue().get_limit()
        finally:
            application_context.close()
