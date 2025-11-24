from micronaut.runtime.event.annotation import EventListener
from micronaut.context.event import StartupEvent
from jakarta.inject import Singleton

@Singleton
class MyListener:
    @EventListener
    def start(self, event: StartupEvent):
        print("Hello from listener")
