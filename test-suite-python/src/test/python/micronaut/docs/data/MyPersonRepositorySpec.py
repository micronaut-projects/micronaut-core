from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context.annotation import Property
from org.junit.jupiter.api import Test, Disabled
from jakarta.inject import Inject
from typing import Annotated
from .MyPersonRepository import MyPersonRepository
from .MyPersonRepository import MyPersonRepositoryInitializer
from .MyPersonRepository import MyPerson
import java


ArrayList = java.type("java.util.ArrayList")


def java_list(*values):
    result = ArrayList()
    for value in values:
        result.add(value)
    return result


def unwrap_optional(value):
    if hasattr(value, "isPresent"):
        assert value.isPresent()
        return value.get()
    assert value is not None
    return value


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
        person_by_name = {person.name: person for person in people}
        assert {"Constructor", "Denis", "Graeme"}.issubset(set(person_by_name.keys()))
        person = person_by_name["Denis"]
        print("Load by id")
        denis = self.myPersonRepository.findAllById(person.id)
        print(denis)
        assert denis.name == "Denis"

        denis = self.myPersonRepository.findById(person.id).get()
        print(denis)
        assert denis.name == "Denis"

    @Test
    def testCustomSaveMethodAndFinder(self) -> None:
        self.myPersonRepository.savePerson(MyPerson(-1, "Custom Save", 20))

        custom_saved = self.myPersonRepository.findByName("Custom Save")
        assert custom_saved is not None
        assert custom_saved.name == "Custom Save"

        people = self.myPersonRepository.findAll()
        names = {person.name for person in people}
        assert "Custom Save" in names

    @Test
    def testSaveCountAndFindAll(self) -> None:
        before = self.myPersonRepository.count()

        crud_saved = self.myPersonRepository.save(MyPerson(-1, "Crud Save", 21))
        assert crud_saved.id > 0
        assert crud_saved.name == "Crud Save"

        assert self.myPersonRepository.count() == before + 1

        people = self.myPersonRepository.findAll()
        names = {person.name for person in people}
        assert "Crud Save" in names

    @Test
    def testExistsById(self) -> None:
        crud_saved = self.myPersonRepository.save(MyPerson(-1, "Crud Exists", 22))
        assert self.myPersonRepository.existsById(crud_saved.id)

    @Test
    def testFindById(self) -> None:
        crud_saved = self.myPersonRepository.save(MyPerson(-1, "Crud Find By Id", 23))
        found_by_id = unwrap_optional(self.myPersonRepository.findById(crud_saved.id))
        assert found_by_id.name == "Crud Find By Id"

    @Test
    def testUpdate(self) -> None:
        crud_saved = self.myPersonRepository.save(MyPerson(-1, "Crud Save For Update", 24))

        updated = self.myPersonRepository.update(MyPerson(crud_saved.id, "Crud Update", 25))
        assert updated.id == crud_saved.id
        assert updated.name == "Crud Update"
        assert self.myPersonRepository.findByName("Crud Update").id == crud_saved.id

    @Test
    def testDeleteById(self) -> None:
        crud_saved = self.myPersonRepository.save(MyPerson(-1, "Crud Delete By Id", 26))
        before = self.myPersonRepository.count()

        self.myPersonRepository.deleteById(crud_saved.id)

        assert self.myPersonRepository.count() == before - 1
        assert not self.myPersonRepository.findById(crud_saved.id).isPresent()

    @Test
    def testDeleteEntity(self) -> None:
        crud_saved = self.myPersonRepository.save(MyPerson(-1, "Crud Delete", 27))
        before = self.myPersonRepository.count()

        self.myPersonRepository.delete(crud_saved)

        assert self.myPersonRepository.count() == before - 1
        assert not self.myPersonRepository.findById(crud_saved.id).isPresent()

    @Test
    def testDeleteAll(self) -> None:
        saved_a = self.myPersonRepository.save(MyPerson(-1, "Crud Delete All A", 28))
        saved_b = self.myPersonRepository.save(MyPerson(-1, "Crud Delete All B", 29))
        before = self.myPersonRepository.count()

        self.myPersonRepository.deleteAll()

        assert self.myPersonRepository.count() == 0
        assert before >= 2
        assert not self.myPersonRepository.findById(saved_a.id).isPresent()
        assert not self.myPersonRepository.findById(saved_b.id).isPresent()

    @Test
    def testBulkCrudMethods(self) -> None:
        batch_saved = self.myPersonRepository.saveAll(java_list(
            MyPerson(-1, "Crud Save All A", 30),
            MyPerson(-1, "Crud Save All B", 31),
        ))
        assert len(batch_saved) == 2

        batch_saved[0].name = "Crud Update All A"
        batch_saved[1].name = "Crud Update All B"
        batch_updated = self.myPersonRepository.updateAll(batch_saved)
        batch_updated_names = {person.name for person in batch_updated}
        assert batch_updated_names == {"Crud Update All A", "Crud Update All B"}

        before_delete = self.myPersonRepository.count()
        self.myPersonRepository.delete(batch_updated[0])
        assert self.myPersonRepository.count() == before_delete - 1

        self.myPersonRepository.deleteAll(java_list(batch_updated[1]))
        assert self.myPersonRepository.count() == before_delete - 2
