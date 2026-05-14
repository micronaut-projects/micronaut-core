package io.micronaut.python.annotation.processing.test.event

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.context.event.StartupEvent
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import org.graalvm.polyglot.Value
import spock.lang.PendingFeature
import spock.util.concurrent.PollingConditions

class PythonEventListenerSpec extends AbstractPythonTypeElementSpec {
    void "test event listener method adapter exposes metadata and event type"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.context.event import StartupEvent
from micronaut.runtime.event.annotation import EventListener

@Requires(property="feature.enabled", value="true")
@Singleton
class StartupListener:
    invoked: bool = False

    @EventListener
    def on_startup(self, event: StartupEvent):
        self.invoked = True
''', false, ["feature.enabled": "true"])

        when:
        def listenerDefinitions = context.getBeanDefinitions(ApplicationEventListener)
        def definition = listenerDefinitions.find {
            it.annotationMetadata.stringValue(Requires, "property").orElse(null) == "feature.enabled"
        }

        then:
        definition != null
        definition.annotationMetadata.stringValue(Requires, "value").get() == "true"
        !definition.getTypeArguments(ApplicationEventListener).isEmpty()
        definition.getTypeArguments(ApplicationEventListener).get(0).type == StartupEvent

        cleanup:
        context?.close()
    }

    void "test event listener with failing requirements is not present"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.runtime.event.annotation import EventListener

@dataclass
class SampleEvent:
    message : str = "Something happened"

@Requires(property="not.present")
@Singleton
class DisabledSampleEventListener:
    invocation_count : int = 0

    @EventListener
    def on_sample_event(self, event : SampleEvent):
        self.invocation_count += 1
''')
        def listenerType = context.classLoader.loadClass("python.DisabledSampleEventListener")

        expect:
        !context.containsBean(listenerType)
        context.getBeansOfType(ApplicationEventListener).isEmpty()

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "GraalPy has a bug that doesn't allow constructors for types that implement a java interface")
    void "test python event listener via interface with java event"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from jakarta.inject import Singleton, Inject
from micronaut.context.event import ApplicationEventListener, StartupEvent
from micronaut.context.annotation import Executable

@dataclass
class SampleEvent:
    message : str = "Something happened"

@Singleton
class CounterService:
    invocation_count : int = 0
    def increment(self):
        self.invocation_count = self.invocation_count + 1

    @Executable
    def get_count(self) -> int:
        return self.invocation_count

@Singleton
class SampleEventListener(ApplicationEventListener[StartupEvent]):
    invocation_count : int = 0
    def __init__(self, counter : CounterService):
        self.counter = counter

    def onApplicationEvent(self, event : StartupEvent):
        counter.increment()


''')

        when:
        def event = context.classLoader.loadClass('python.SampleEvent').newInstance("test")
        context.publishEvent(event)
        def counterService = getBean(context, "python.CounterService")


        then:
        counterService.get_count() == 1
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

    void "test multiple event listener methods on same bean"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.event import ShutdownEvent, StartupEvent
from micronaut.runtime.event.annotation import EventListener

@Singleton
class LifecycleEventListener:
    invoked : bool = False
    shutdown : bool = False

    @EventListener
    def receive_startup(self, event : StartupEvent):
        self.invoked = True

    @EventListener
    def receive_shutdown(self, event : ShutdownEvent):
        self.shutdown = True
''')
        Value value = getBean(context, "python.LifecycleEventListener").asPolyglotValue()

        expect:
        value.getMember("invoked").asBoolean()

        when:
        context.publishEvent(new ShutdownEvent(context))

        then:
        value.getMember("shutdown").asBoolean()

        cleanup:
        context?.close()
    }

    void "test python event listener via annotation with python event"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.runtime.event.annotation import EventListener

@dataclass
class SampleEvent:
    message : str = "Something happened"

@Singleton
class SampleEventListener:
    invocation_count : int = 0

    @EventListener
    def on_sample_event(self, event : SampleEvent):
        self.invocation_count += 1
''')

        when:
        def event = context.classLoader.loadClass('python.SampleEvent').newInstance("test")
        context.publishEvent(event)
        Value value = getBean(context, "python.SampleEventListener").asPolyglotValue()

        then:
        value.getMember("invocation_count").asInt() == 1

        cleanup:
        context?.close()
    }

    void "test async python event listener via annotation with python event"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.runtime.event.annotation import EventListener
from micronaut.scheduling.annotation import Async

@dataclass
class SampleEvent:
    message : str = "Something happened"

@Singleton
class SampleEventListener:
    invocation_count : int = 0

    @EventListener
    @Async
    def on_sample_event(self, event : SampleEvent):
        self.invocation_count += 1
''')

        when:
        def event = context.classLoader.loadClass('python.SampleEvent').newInstance("test")
        context.publishEvent(event)
        Value value = getBean(context, "python.SampleEventListener").asPolyglotValue()

        then:
        new PollingConditions(timeout: 5).eventually {
            assert value.getMember("invocation_count").asInt() == 1
        }

        cleanup:
        context?.close()
    }

}
