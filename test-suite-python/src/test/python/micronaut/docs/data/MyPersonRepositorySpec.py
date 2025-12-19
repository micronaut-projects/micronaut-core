from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from jakarta.inject import Inject
from typing import Annotated
from .MyPersonRepository import MyPersonRepository
from .MyPersonRepository import MyPerson


# :test-suite-python:test --tests "*MyPersonRepositorySpec*"
@MicronautTest(transactional = False)
class MyPersonRepositorySpec:
    myPersonRepository : Annotated[MyPersonRepository, Inject] = None

    @Test
    def testDBAccess(self) -> None:
        self.myPersonRepository.savePerson(MyPerson(-1, "Denis", 123))
        print(self.myPersonRepository)
        print("Load all")
        people = self.myPersonRepository.findAll()
        print(people)
        assert len(people) == 1
        person = people[0]
        assert person.name == "Denis"
        print("Load by id")
        denis = self.myPersonRepository.findAllById(person.id)
        print(denis)
        assert denis.name == "Denis"
