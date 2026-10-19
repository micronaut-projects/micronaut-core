from dataclasses import dataclass
from datetime import datetime
from typing import Annotated

from java.time import Instant, OffsetDateTime
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository


@MappedEntity("fetch_log")
@dataclass
class FetchLogEntry:
    id: Annotated[int | None, Id, GeneratedValue]
    run: Annotated[datetime, Instant]
    finished: Annotated[datetime | None, OffsetDateTime]
    local_run: datetime


@JdbcRepository(dialect="H2")
class FetchLogRepository(CrudRepository[FetchLogEntry, int]):
    def findByRunAfter(self, run: Annotated[datetime, Instant]) -> list[FetchLogEntry]: ...
