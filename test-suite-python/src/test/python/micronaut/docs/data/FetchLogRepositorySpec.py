from datetime import datetime, timedelta, timezone
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .FetchLogRepository import FetchLogEntry, FetchLogRepository


@MicronautTest(transactional=False)
@Property(name="datasources.default.url", value="jdbc:h2:mem:fetchLogDb;DB_CLOSE_ON_EXIT=FALSE")
@Property(name="datasources.default.schema-generate", value="CREATE_DROP")
@Property(name="datasources.default.dialect", value="H2")
class FetchLogRepositorySpec:
    repository: Annotated[FetchLogRepository, Inject] = None

    @Test
    def aware_datetimes_round_trip_through_jdbc(self) -> None:
        # micronaut-projects/pyronaut#334
        run = datetime(2026, 10, 7, tzinfo=timezone.utc)
        finished = datetime(2026, 10, 7, 5, 30, 15, 250000, tzinfo=timezone(timedelta(hours=5, minutes=30)))
        saved = self.repository.save(FetchLogEntry(None, run, finished, datetime(2026, 10, 7)))
        assert saved.id is not None

        found = self.repository.findById(saved.id).get()
        assert found.run.tzinfo is not None
        assert found.run == run
        assert found.finished.tzinfo is not None
        assert found.finished == finished
        assert found.local_run == datetime(2026, 10, 7)
        assert found.local_run.tzinfo is None

        assert [entry.id for entry in self.repository.findByRunAfter(run - timedelta(seconds=1))] == [saved.id]
        assert self.repository.findByRunAfter(run) == []
