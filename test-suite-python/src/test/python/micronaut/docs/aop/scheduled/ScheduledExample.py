from micronaut.context.annotation import Requires

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.scheduling.annotation import Scheduled
# end::imports[]


@Requires(property="spec.name", value="ScheduledExampleSpec")
@Singleton
class ScheduledExample:
    # tag::fixedRate[]
    @Scheduled(fixedRate="5m")
    def every_five_minutes(self) -> None:
        print("Executing every_five_minutes()")
    # end::fixedRate[]

    # tag::fixedDelay[]
    @Scheduled(fixedDelay="5m")
    def five_minutes_after_last_execution(self) -> None:
        print("Executing five_minutes_after_last_execution()")
    # end::fixedDelay[]

    # tag::cron[]
    @Scheduled(cron="0 15 10 ? * MON")
    def every_monday_at_ten_fifteen_am(self) -> None:
        print("Executing every_monday_at_ten_fifteen_am()")
    # end::cron[]

    # tag::initialDelay[]
    @Scheduled(initialDelay="1m")
    def once_one_minute_after_startup(self) -> None:
        print("Executing once_one_minute_after_startup()")
    # end::initialDelay[]

    # tag::configured[]
    @Scheduled(fixedRate="${my.task.rate:5m}",
               initialDelay="${my.task.delay:1m}")
    def configured_task(self) -> None:
        print("Executing configured_task()")
    # end::configured[]
