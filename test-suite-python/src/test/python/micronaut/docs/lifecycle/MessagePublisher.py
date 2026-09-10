# tag::class[]
from jakarta.annotation import PreDestroy
from jakarta.inject import Singleton

from . import ShutdownLog


@Singleton
class MessagePublisher:

    def __init__(self):
        ShutdownLog.add("publisher created")

    @PreDestroy
    def close(self):  # <3>
        ShutdownLog.add("publisher closed")
# end::class[]
