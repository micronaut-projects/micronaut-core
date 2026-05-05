
import java
Dialect = java.type("io.micronaut.data.model.query.builder.sql.Dialect")

from dataclasses import dataclass
from micronaut.data.annotation import MappedEntity
from micronaut.data.annotation import Id
from micronaut.data.annotation import GeneratedValue
from typing import Annotated
from typing import List
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository
from jakarta.data.repository import Save
from jakarta.inject import Singleton

@dataclass
@MappedEntity
class MyPerson:
    id : Annotated[int, Id, GeneratedValue]
    name : str
    age : int

@JdbcRepository(dialect = "H2")
class MyPersonRepository(CrudRepository[MyPerson, int]):

    @Save
    def savePerson(self, person : MyPerson) -> None: ...

    def findAll(self) -> List[MyPerson]: ...

    def findAllById(self, id: int) -> MyPerson: ...

    def findByName(self, name: str) -> MyPerson: ...


@Singleton
class MyPersonRepositoryInitializer:
    def __init__(self, repository: MyPersonRepository):
        repository.savePerson(MyPerson(-3, "Constructor", 20))
