import java

from typing import Annotated

from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


# tag::config[]
@Property(name="test.datasource.one.url", value="jdbc:mysql://localhost/one")
@Property(name="test.datasource.two.url", value="jdbc:mysql://localhost/two")
# end::config[]
@Property(name="ratelimits[0].period", value="10s")
@Property(name="ratelimits[0].limit", value="1000")
@Property(name="ratelimits[1].period", value="1m")
@Property(name="ratelimits[1].limit", value="5000")
@MicronautTest
class EachPropertyTest:
    context: Annotated[ApplicationContext, Inject] = None

    @Test
    def test_each_property(self) -> None:
        # tag::beans[]
        DataSourceConfiguration = java.type(
            "micronaut.docs.config.env.DataSourceConfiguration"
        )
        beans_of_type = self.context.getBeansOfType(DataSourceConfiguration)
        assert 2 == beans_of_type.size()  # <1>

        first_config = self.context.getBean(
            DataSourceConfiguration,
            Qualifiers.byName("one"),  # <2>
        )

        URI = java.type("java.net.URI")
        assert URI("jdbc:mysql://localhost/one").equals(first_config.url)
        # end::beans[]

    @Test
    def test_each_property_list(self) -> None:
        RateLimitsConfiguration = java.type(
            "micronaut.docs.config.env.RateLimitsConfiguration"
        )
        beans_of_type = self.context.streamOfType(RateLimitsConfiguration).toList()
        limits_by_index = {
            beans_of_type.get(i).index: beans_of_type.get(i).limit
            for i in range(beans_of_type.size())
        }

        assert 2 == beans_of_type.size()
        assert {0: 1000, 1: 5000} == limits_by_index
