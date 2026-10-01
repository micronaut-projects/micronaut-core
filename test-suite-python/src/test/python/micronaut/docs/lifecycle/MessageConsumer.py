# tag::class[]
from jakarta.annotation import PreDestroy
from jakarta.inject import Singleton
from micronaut.context.annotation import DependsOn

from . import ShutdownLog
from .MessagePublisher import MessagePublisher


@Singleton
@DependsOn(MessagePublisher)  # <1>
class MessageConsumer:

    def __init__(self):
        ShutdownLog.add("consumer created")

    @PreDestroy
    def stop(self):  # <2>
        ShutdownLog.add("consumer stopped")
# end::class[]
