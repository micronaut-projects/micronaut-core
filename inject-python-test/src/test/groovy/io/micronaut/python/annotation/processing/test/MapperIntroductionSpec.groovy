package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext

class MapperIntroductionSpec extends AbstractPythonTypeElementSpec {

    void "method level mapper creates bean for abstract Python class"() {
        given:
        String py = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from micronaut.context.annotation import Mapper
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class ContactForm:
    first_name: str
    last_name: str


@Introspected
@dataclass
class ContactEntity:
    first_name: str
    last_name: str


class ContactMappers(ABC):
    @Mapper
    @abstractmethod
    def to_entity(self, contact_form: ContactForm) -> ContactEntity:
        pass


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Class<?> mapperClass = ctx.classLoader.loadClass('python.ContactMappers')
        def mapper = ctx.getBean(mapperClass)

        then:
        mapper != null

        cleanup:
        ctx?.close()
    }
}
