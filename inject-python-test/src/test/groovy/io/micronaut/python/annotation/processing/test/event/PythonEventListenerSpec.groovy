package io.micronaut.python.annotation.processing.test.event

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import org.graalvm.polyglot.Value

class PythonEventListenerSpec extends AbstractPythonTypeElementSpec {

    void "test python event listener via interface with java event"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from jakarta.inject import Singleton, Inject
from micronaut.context.event import ApplicationEventListener, StartupEvent

@dataclass
class SampleEvent:
    message : str = "Something happened"


@Singleton
class SampleEventListener(ApplicationEventListener[StartupEvent]):
    invocation_count : int = 0

    def onApplicationEvent(self, event : StartupEvent):
        self.invocation_count += 1
''')

        when:
        def event = context.classLoader.loadClass('python.SampleEvent').newInstance("test")
        context.publishEvent(event)

        Value value = getBean(context, "python.SampleEventListener").asPolyglotValue()

        then:
        value.invocation_count == 1
    }

    void "test python event listener via annotation with java event"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from jakarta.inject import Singleton, Inject
from micronaut.context.event import ApplicationEventListener, StartupEvent
from micronaut.runtime.event.annotation import EventListener

@dataclass
class SampleEvent:
    message : str = "Something happened"


@Singleton
class SampleEventListener:
    invocation_count : int = 0
    @EventListener
    def onApplicationEvent(self, event : StartupEvent):
        print("RECEIVED EVENT 1")
        self.invocation_count += 1
        print(f"COUNT {self.invocation_count}")


@Singleton
class SampleEventListener2:
    invocation_count : int = 0
    @EventListener
    def onApplicationEvent2(self, event : StartupEvent):
        print("RECEIVED EVENT 2")
        self.invocation_count += 1
        print(f"COUNT {self.invocation_count}")
''')

        when:
        def event = context.classLoader.loadClass('python.SampleEvent').newInstance("test")
        context.publishEvent(event)

        Value value = getBean(context, "python.SampleEventListener").asPolyglotValue()
        Value value2 = getBean(context, "python.SampleEventListener2").asPolyglotValue()

        then:
        value.getMember("invocation_count").asInt() == 1
        value2.getMember("invocation_count").asInt() == 1
    }

}
