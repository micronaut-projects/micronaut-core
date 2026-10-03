from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from micronaut.docs.hibernate.BookRepository import BookRepository


# :test-suite-python:test --tests "*hibernate.BookRepositorySpec*"
@MicronautTest(transactional=False)
@Property(name="jpa.enabled", value="true")
@Property(name="datasources.default.url", value="jdbc:h2:mem:hibernateDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name="datasources.default.dialect", value="H2")
@Property(name="jpa.default.properties.hibernate.hbm2ddl.auto", value="create-drop")
@Property(name="jpa.default.compile-time-hibernate-proxies", value="true")
class BookRepositorySpec:
    book_repository: Annotated[BookRepository, Inject]

    @Test
    def test_python_entity_is_persisted(self):
        book = self.book_repository.save("The Stand", 1000)
        assert book.id is not None
        loaded = self.book_repository.find_by_id(book.id)
        assert loaded.title == "The Stand"
        assert loaded.pages == 1000
        assert "The Stand" in self.book_repository.find_titles()

    @Test
    def test_embedded_id(self):
        self.book_repository.save_order("EU", 42, "Fred")
        order = self.book_repository.find_order("EU", 42)
        assert order is not None
        assert order.customer == "Fred"
        assert order.id.region == "EU"

    @Test
    def test_lazy_owner_is_a_compile_time_proxy(self):
        pet = self.book_repository.save_pet("Dino", "Fred")
        assert self.book_repository.is_owner_lazily_loaded(pet.id)
        assert self.book_repository.owner_is_hibernate_proxy(pet.id)
        assert self.book_repository.owner_name(pet.id) == "Fred"
