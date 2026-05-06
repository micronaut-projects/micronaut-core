from micronaut.context import ApplicationContext
from org.junit.jupiter.api import Disabled, Test

from .ContactForm import ContactForm
from .ContactMappers import ContactMappers


@Disabled("Python @Mapper interface introduction is not fully validated yet")
class SimpleMapperSpec:
    @Test
    def test_simple_mappers(self) -> None:
        context = ApplicationContext.run({"spec.name": "SimpleMapperSpec"})
        try:
            # tag::mappers[]
            contact_mappers = context.getBean(ContactMappers)
            contact_entity = contact_mappers.to_entity(ContactForm("John", "Snow"))
            assert contact_entity.first_name == "John"
            assert contact_entity.last_name == "Snow"
            # end::mappers[]
        finally:
            context.close()
