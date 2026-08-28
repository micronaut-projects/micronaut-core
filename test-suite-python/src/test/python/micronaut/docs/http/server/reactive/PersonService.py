from jakarta.inject import Singleton
from micronaut.docs.ioc.beans.Person import Person


@Singleton
class PersonService:
    def findByName(self, name: str) -> Person:
        return Person(name, 18)
