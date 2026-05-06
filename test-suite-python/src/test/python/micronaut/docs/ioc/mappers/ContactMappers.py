from abc import ABC, abstractmethod

from .ContactEntity import ContactEntity
from .ContactForm import ContactForm

# tag::imports[]
from micronaut.context.annotation import Mapper
# end::imports[]


# tag::class[]
class ContactMappers(ABC):
    @Mapper
    @abstractmethod
    def to_entity(self, contact_form: ContactForm) -> ContactEntity:
        pass
# end::class[]
