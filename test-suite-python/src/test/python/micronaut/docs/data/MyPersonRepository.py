
import java
Dialect = java.type("io.micronaut.data.model.query.builder.sql.Dialect")

from dataclasses import dataclass
from micronaut.data.annotation import MappedEntity
from micronaut.data.annotation import Id
from typing import Annotated
from typing import List
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository
from abc import ABC, abstractmethod
from jakarta.data.repository import Save

@dataclass
@MappedEntity
class MyPerson:
    id : Annotated[int, Id]
    name : str
    age : int

@JdbcRepository(dialect = "H2")
class MyPersonRepository(CrudRepository[MyPerson, int], new_style=True):

    @Save
    @abstractmethod
    def savePerson(self, person : MyPerson) -> None:
        pass

    @abstractmethod
    def findAll(self) -> List[MyPerson]:
        pass

    @abstractmethod
    def findAllById(self, id: int) -> MyPerson:
        pass
