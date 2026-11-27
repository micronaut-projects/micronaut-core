import builtins
from dataclasses import dataclass
from enum import Enum
from typing import Annotated, Protocol

from micronaut.context.annotation import Requires
from micronaut.context.python.scope import ContextPooled
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository
from micronaut.http.annotation import Controller, Get


class TaskStatus(Enum):
    OPEN = "open"
    DONE = "done"


@MappedEntity("tracked_tasks")
@dataclass
class TrackedTask:
    id: Annotated[int | None, Id, GeneratedValue]
    title: str
    status: TaskStatus
    note: str | None = None


@Requires(property="spec.name", value="TrackedTaskRepositorySpec")
@JdbcRepository(dialect=Dialect.H2)
class TrackedTaskRepository(CrudRepository[TrackedTask, int], Protocol):
    pass


# The routes run in a pooled context, while the entities the repository returns are generated Java
# objects whose Python objects Java created in the primary context.
@Requires(property="spec.name", value="TrackedTaskRepositorySpec")
@Controller("/tracked-tasks")
@ContextPooled
class TrackedTaskController:
    def __init__(self, tasks: TrackedTaskRepository):
        self.tasks = tasks

    @Get("/{taskId}/status")
    def status(self, taskId: int) -> str:
        task = self.tasks.findById(taskId).orElse(None)
        status = task.status
        expected = TaskStatus[status.name]
        counts = {s.name: sum(1 for t in self.tasks.findAll() if t.status == s) for s in TaskStatus}
        return "|".join([
            f"{type(status).__module__}.{type(status).__qualname__}",
            str(status == expected),
            str(status is expected),
            str(isinstance(status, TaskStatus)),
            str(hash(status) == hash(expected)),
            f"{counts['OPEN']},{counts['DONE']}",
            str(builtins.__MN_CTX_ID__),
        ])

    @Get("/{taskId}/note")
    def note(self, taskId: int) -> str:
        note = self.tasks.findById(taskId).orElse(None).note
        return f"{note is None}|{type(note).__name__}|{note == None}"  # noqa: E711
