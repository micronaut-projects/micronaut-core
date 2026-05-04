from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context.annotation import Property
from org.junit.jupiter.api import Test, Disabled
from jakarta.inject import Inject
from typing import Annotated
from .MyPersonRepository import MyPersonRepository
from .MyPersonRepository import MyPersonRepositoryInitializer
from .MyPersonRepository import MyPerson


# :test-suite-python:test --tests "*MyPersonRepositorySpec*"
@MicronautTest(transactional = False)
@Property(name = "datasources.default.url", value = "jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class MyPersonRepositorySpec:
    myPersonRepository : Annotated[MyPersonRepository, Inject] = None
    initializer : Annotated[MyPersonRepositoryInitializer, Inject] = None

    @Test
    def testDBAccess(self) -> None:
        assert self.initializer is not None
        # print(f"REPOSITORY TYPE {self.myPersonRepository.getClass().getName()}")
        self.myPersonRepository.save(MyPerson(-1, "Denis", 123))
        self.myPersonRepository.savePerson(MyPerson(-2, "Graeme", 456))
        print(self.myPersonRepository)
        print("Load all")
        people = self.myPersonRepository.findAll()
        print(people)
        assert len(people) == 2
        person_by_name = {person.name: person for person in people}
        assert set(person_by_name.keys()) == {"Denis", "Graeme"}
        person = person_by_name["Denis"]
        print("Load by id")
        denis = self.myPersonRepository.findAllById(person.id)
        print(denis)
        assert denis.name == "Denis"

        denis = self.myPersonRepository.findById(person.id).get()
        print(denis)
        assert denis.name == "Denis"
