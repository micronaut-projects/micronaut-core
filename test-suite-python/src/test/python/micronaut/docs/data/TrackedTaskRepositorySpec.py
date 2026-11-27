import builtins
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .TrackedTaskRepository import TaskStatus, TrackedTask, TrackedTaskRepository


# An Enum member read from an entity in a pooled context is the member of that context: it compares
# equal to (and is) the member the route names, rather than a foreign object of the context Java
# created the entity's Python object in, which compared equal to nothing (pyronaut#439).
# :test-suite-python:test --tests "*TrackedTaskRepositorySpec*"
@MicronautTest(transactional=False)
@Property(name="spec.name", value="TrackedTaskRepositorySpec")
@Property(name="micronaut.python.pool.enabled", value="true")
@Property(name="micronaut.python.pool.size", value="2")
@Property(name="datasources.default.url", value="jdbc:h2:mem:trackedTasks;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name="datasources.default.schema-generate", value="CREATE_DROP")
@Property(name="datasources.default.dialect", value="H2")
class TrackedTaskRepositorySpec:
    tasks: Annotated[TrackedTaskRepository, Inject] = None
    client: Annotated[HttpClient, Inject, Client("/")] = None

    @Test
    def enumMemberEqualsTheMemberOfThePooledContext(self) -> None:
        self.tasks.deleteAll()
        done = self.tasks.save(TrackedTask(None, "Write", TaskStatus.DONE))
        self.tasks.save(TrackedTask(None, "Read", TaskStatus.OPEN))
        self.tasks.save(TrackedTask(None, "Review", TaskStatus.OPEN))
        for _ in range(4):
            description = self.client.toBlocking().retrieve(f"/tracked-tasks/{done.id}/status")
            type_name, equal, identical, instance, same_hash, counts, context_id = description.split("|")
            # the route ran in a pooled context, not in the one of this test
            assert context_id != str(builtins.__MN_CTX_ID__), description
            assert type_name.endswith("TrackedTaskRepository.TaskStatus"), description
            assert equal == "True", description
            assert identical == "True", description
            assert instance == "True", description
            assert same_hash == "True", description
            assert counts == "2,1", description

    @Test
    def noneMemberIsNoneInThePooledContext(self) -> None:
        task = self.tasks.save(TrackedTask(None, "Plan", TaskStatus.OPEN))
        for _ in range(4):
            description = self.client.toBlocking().retrieve(f"/tracked-tasks/{task.id}/note")
            assert description == "True|NoneType|True", description
