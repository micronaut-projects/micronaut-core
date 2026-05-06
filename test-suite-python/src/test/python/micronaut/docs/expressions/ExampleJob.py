from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.scheduling.annotation import Scheduled
from jakarta.inject import Singleton
# end::imports[]


@Requires(property="spec.name", value="ExampleJobTest")
# tag::clazz[]
@Singleton
class ExampleJob:
    job_ran: bool = False
    paused: bool = True

    @Scheduled(
        fixedRate="1s",
        condition="#{!this.paused}")  # <1>
    def run(self) -> None:
        print("Job Running")
        self.job_ran = True

    def is_paused(self) -> bool:
        return self.paused
    # <2>

    def has_job_run(self) -> bool:
        return self.job_ran

    def unpause(self) -> None:
        self.paused = False

    def pause(self) -> None:
        self.paused = True
# end::clazz[]
