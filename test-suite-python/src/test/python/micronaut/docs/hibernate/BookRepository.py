from typing import Annotated

import java
from jakarta.inject import Singleton
from jakarta.persistence import EntityManager, PersistenceContext
from micronaut.transaction.annotation import Transactional

from micronaut.docs.hibernate.Book import Book
from micronaut.docs.hibernate.Order import Order
from micronaut.docs.hibernate.OrderId import OrderId
from micronaut.docs.hibernate.Pet import Pet

BookClass = java.type("micronaut.docs.hibernate.Book")
OrderClass = java.type("micronaut.docs.hibernate.Order")
PetClass = java.type("micronaut.docs.hibernate.Pet")
OwnerClass = java.type("micronaut.docs.hibernate.Owner")
HibernateProxy = java.type("org.hibernate.proxy.HibernateProxy")
Hibernate = java.type("org.hibernate.Hibernate")


# tag::clazz[]
@Singleton
class BookRepository:
    entity_manager: Annotated[EntityManager, PersistenceContext]

    @Transactional
    def save(self, title: str, pages: int) -> Book:
        # Hibernate assigns the identifier to the Java instance it manages: persist the Java
        # class generated for the Python entity rather than a Python object, whose copy
        # the entity manager would otherwise persist.
        book = BookClass(title, pages, None)
        self.entity_manager.persist(book)
        return book

    @Transactional(readOnly=True)
    def find_by_id(self, id: int) -> Book | None:
        return self.entity_manager.find(BookClass, id)

    @Transactional(readOnly=True)
    def find_titles(self) -> list[str]:
        return list(self.entity_manager.createQuery("select b.title from Book b order by b.title", java.type("java.lang.String")).getResultList())
# end::clazz[]

    @Transactional
    def save_order(self, region: str, number: int, customer: str) -> Order:
        order = Order()
        order.id = OrderId(region, number)
        order.customer = customer
        self.entity_manager.persist(order)
        return order

    @Transactional(readOnly=True)
    def find_order(self, region: str, number: int) -> Order | None:
        return self.entity_manager.find(OrderClass, OrderId(region, number))

    @Transactional
    def save_pet(self, pet_name: str, owner_name: str) -> Pet:
        owner = OwnerClass()
        owner.name = owner_name
        self.entity_manager.persist(owner)
        pet = PetClass()
        pet.name = pet_name
        pet.owner = owner
        self.entity_manager.persist(pet)
        return pet

    @Transactional(readOnly=True)
    def owner_is_hibernate_proxy(self, pet_id: int) -> bool:
        pet = self.entity_manager.find(PetClass, pet_id)
        return HibernateProxy.class_.isInstance(pet.owner)

    @Transactional(readOnly=True)
    def is_owner_lazily_loaded(self, pet_id: int) -> bool:
        pet = self.entity_manager.find(PetClass, pet_id)
        return not Hibernate.isInitialized(pet.owner)

    @Transactional(readOnly=True)
    def owner_name(self, pet_id: int) -> str:
        pet = self.entity_manager.find(PetClass, pet_id)
        # the proxy loads the owner on first use, Hibernate.unproxy returns the loaded entity
        return Hibernate.unproxy(pet.owner).name
