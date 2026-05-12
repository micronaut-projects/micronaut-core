import java

from micronaut.context import ApplicationContext
from micronaut.context.env import PropertySource
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class EachBeanTest:
    @Test
    def test_each_bean(self) -> None:
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
            DataSource = java.type("micronaut.docs.config.env.DataSource")
            beans_of_type = application_context.getBeansOfType(DataSource)
            assert 2 == beans_of_type.size()  # <1>

            first_config = application_context.getBean(
                DataSource,
                Qualifiers.byName("one"),  # <2>
            )
            # end::beans[]

            assert first_config is not None
        finally:
            application_context.close()
