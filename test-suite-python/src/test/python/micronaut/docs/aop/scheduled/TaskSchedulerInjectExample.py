from typing import Annotated

import java
from jakarta.inject import Inject, Named

TaskExecutors = java.type("io.micronaut.scheduling.TaskExecutors")
TaskScheduler = java.type("io.micronaut.scheduling.TaskScheduler")


class TaskSchedulerInjectExample:
    # tag::inject[]
    task_scheduler: Annotated[TaskScheduler, Inject, Named(TaskExecutors.SCHEDULED)] = None
    # end::inject[]
