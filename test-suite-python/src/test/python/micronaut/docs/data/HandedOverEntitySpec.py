from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .HandedOverEntityRepository import ShelfStatus, ShelvedBook, ShelvedBookRepository


# The entities of a Java collection that Python iterates or indexes are handed over as their Python
# objects (plain attribute reads, dataclasses.asdict, isinstance), and the attributes Python writes on them
# reach the Java wrappers that the collection holds, so that saving the collection saves the writes.
# :test-suite-python:test --tests "*HandedOverEntitySpec*"
@MicronautTest(transactional=False)
@Property(name="spec.name", value="HandedOverEntitySpec")
@Property(name="micronaut.python.pool.enabled", value="true")
@Property(name="micronaut.python.pool.size", value="2")
@Property(name="datasources.default.url", value="jdbc:h2:mem:handedOver;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name="datasources.default.schema-generate", value="CREATE_DROP")
@Property(name="datasources.default.dialect", value="H2")
class HandedOverEntitySpec:
    books: Annotated[ShelvedBookRepository, Inject] = None
    client: Annotated[HttpClient, Inject, Client("/")] = None

    def _seed(self):
        self.books.deleteAll()
        self.books.save(ShelvedBook(None, "A", 10, ShelfStatus.DONE))
        self.books.save(ShelvedBook(None, "B", 20, ShelfStatus.TO_READ))
        self.books.save(ShelvedBook(None, "C", 30, ShelfStatus.DONE))

    def _check_describe(self, path):
        self._seed()
        for _ in range(3):
            description = self.client.toBlocking().retrieve(f"{path}/describe")
            assert description == "ShelvedBook|True|['A', 'B', 'C']|2|2", description

    def _check_edit_all(self, path):
        self._seed()
        edited = self.client.toBlocking().retrieve(f"{path}/edit-all")
        assert edited == "[('A!', 11), ('B!', 21), ('C!', 31)]", edited

    @Test
    def iteratedEntitiesArePythonObjects(self) -> None:
        self._check_describe("/handed-over")

    @Test
    def iteratedEntitiesArePythonObjectsInAPooledContext(self) -> None:
        self._check_describe("/handed-over-pooled")

    @Test
    def writesToIteratedEntitiesAreSaved(self) -> None:
        self._check_edit_all("/handed-over")

    @Test
    def writesToIteratedEntitiesAreSavedFromAPooledContext(self) -> None:
        self._check_edit_all("/handed-over-pooled")
