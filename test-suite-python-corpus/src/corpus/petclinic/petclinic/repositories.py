"""Micronaut Data JDBC repositories.

Repositories are declared as Python ``Protocol`` classes. Pyronaut processes
the Micronaut Data annotations and creates the runtime implementation, so the
methods only need signatures.

Key concepts demonstrated here:

* ``@JdbcRepository`` selects Micronaut Data JDBC and the Oracle SQL dialect.
* ``CrudRepository[T, ID]`` provides standard CRUD methods.
* Method names such as ``findByOwnerIdOrderByName`` are parsed into queries.
* ``@Query`` is useful for native SQL when the method-name query would be too
  limited or when Oracle-specific SQL is clearer.
* ``@Join`` fetches relations eagerly so entity fields can stay non-null.
"""

from typing import Protocol

from micronaut.data.annotation import Join, Query
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from .entities import Owner, Pet, PetType, Speciality, Vet, VetSpeciality, VetWithSpecialities, Visit


@JdbcRepository(dialect=Dialect.ORACLE)
class OwnerRepository(CrudRepository[Owner, int], Protocol):
    """Owner queries used by search and list screens."""

    def findOneById(self, id: int) -> Owner | None: ...

    @Query("SELECT * FROM OWNERS ORDER BY LAST_NAME", nativeQuery=True)
    def findAllOrdered(self) -> list[Owner]: ...

    @Query("SELECT * FROM OWNERS WHERE LOWER(LAST_NAME) LIKE '%' || LOWER(:lastName) || '%' ORDER BY LAST_NAME", nativeQuery=True)
    def findByLastName(self, lastName: str) -> list[Owner]: ...


@JdbcRepository(dialect=Dialect.ORACLE)
class PetRepository(CrudRepository[Pet, int], Protocol):
    """Pet queries that eagerly fetch owner and type relationships."""

    @Join(value="type", type=Join.Type.FETCH)
    @Join(value="owner", type=Join.Type.FETCH)
    def findOneById(self, id: int) -> Pet | None: ...

    @Join(value="type", type=Join.Type.FETCH)
    @Join(value="owner", type=Join.Type.FETCH)
    def findByOwnerIdOrderByName(self, ownerId: int) -> list[Pet]: ...


@JdbcRepository(dialect=Dialect.ORACLE)
class PetTypeRepository(CrudRepository[PetType, int], Protocol):
    """Lookup repository for pet types shown in form select boxes."""

    def findOneById(self, id: int) -> PetType | None: ...

    @Query("SELECT * FROM PET_TYPES ORDER BY NAME", nativeQuery=True)
    def findAllOrderByName(self) -> list[PetType]: ...


@JdbcRepository(dialect=Dialect.ORACLE)
class VisitRepository(CrudRepository[Visit, int], Protocol):
    """Visit queries with joined pet details for visit forms and owner pages."""

    @Join(value="pet", type=Join.Type.FETCH)
    @Join(value="pet.type", type=Join.Type.FETCH)
    @Join(value="pet.owner", type=Join.Type.FETCH)
    def findByPetIdOrderByDateDesc(self, petId: int) -> list[Visit]: ...


@JdbcRepository(dialect=Dialect.ORACLE)
class VetRepository(CrudRepository[Vet, int], Protocol):
    """Veterinarian list queries."""

    @Query(
        """
        SELECT
            v.ID,
            v.FIRST_NAME,
            v.LAST_NAME,
            LISTAGG(
                CASE WHEN s.ID IS NOT NULL THEN s.ID || ':' || s.NAME END,
                '|'
            ) WITHIN GROUP (ORDER BY s.NAME) AS SPECIALITY_ROWS
        FROM VETS v
        LEFT JOIN VET_SPECIALITIES vs ON vs.VET_ID = v.ID
        LEFT JOIN SPECIALITIES s ON s.ID = vs.SPECIALITY_ID
        GROUP BY v.ID, v.FIRST_NAME, v.LAST_NAME
        ORDER BY v.LAST_NAME
        """,
        nativeQuery=True,
    )
    def findAllWithSpecialities(self) -> list[VetWithSpecialities]: ...


@JdbcRepository(dialect=Dialect.ORACLE)
class SpecialityRepository(CrudRepository[Speciality, int], Protocol):
    """Lookup repository for specialities."""

    @Query("SELECT * FROM SPECIALITIES ORDER BY NAME", nativeQuery=True)
    def findAllOrderByName(self) -> list[Speciality]: ...


@JdbcRepository(dialect=Dialect.ORACLE)
class VetSpecialityRepository(CrudRepository[VetSpeciality, int], Protocol):
    """Join-table repository used to persist vet speciality links."""
