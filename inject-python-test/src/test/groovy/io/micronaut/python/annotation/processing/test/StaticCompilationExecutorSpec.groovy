package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Unroll

/**
 * The executor a Python route runs on, interpreted (mode off) and compiled (mode all): the thread
 * name each route returns tells where it ran. An interpreted route runs on the {@code io} executor;
 * a compiled route that never enters Python stays on the event loop, as a Java route does.
 */
class StaticCompilationExecutorSpec extends AbstractPythonTypeElementSpec {

    StaticCompilationMode mode = StaticCompilationMode.OFF
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode)
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String SOURCE = '''
from dataclasses import dataclass
from java.lang import Thread
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Prototype
from micronaut.core.annotation import Introspected
from micronaut.http.annotation import Controller, Get
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn


@Introspected
@dataclass
class Stamp:
    thread: str
    count: int


class Plain:
    def __init__(self, thread: str):
        self.thread = thread


class Rejected(Exception):
    pass


class Refused(Exception):
    def __str__(self) -> str:
        return "refused"


@Singleton
class Clock:
    def thread(self) -> str:
        return Thread.currentThread().getName()


@Controller("/threads")
class ThreadController:
    def __init__(self, clock: Clock):
        self.clock = clock
        self.created = Thread.currentThread()

    @Get(value="/plain", produces="text/plain")
    def plain(self) -> str:
        return Thread.currentThread().getName()

    @Get(value="/sibling", produces="text/plain")
    def sibling(self) -> str:
        return self.named()

    def named(self) -> str:
        return Thread.currentThread().getName()

    @Get(value="/python-sibling", produces="text/plain")
    def python_sibling(self) -> str:
        return self._named()

    def _named(self) -> str:
        return Thread.currentThread().getName()

    @Get(value="/service", produces="text/plain")
    def service(self) -> str:
        return self.clock.thread()

    @Get(value="/created", produces="text/plain")
    def created_on(self) -> str:
        return self.created.getName() + "|" + Thread.currentThread().getName()

    @Get("/introspected")
    def introspected(self) -> Stamp:
        return Stamp(Thread.currentThread().getName(), 1)

    @Get(value="/introspected-read", produces="text/plain")
    def introspected_read(self) -> str:
        stamp = Stamp(Thread.currentThread().getName(), 1)
        return stamp.thread

    @Get(value="/plain-object", produces="text/plain")
    def plain_object(self) -> str:
        name = Thread.currentThread().getName()
        Plain(name)
        return name

    @Get(value="/lazy-raise", produces="text/plain")
    def lazy_raise(self) -> str:
        name = Thread.currentThread().getName()
        try:
            if len(name) > 0:
                raise Rejected(name)
            return "unnamed"
        except Rejected:
            return name

    @Get(value="/eager-raise", produces="text/plain")
    def eager_raise(self) -> str:
        name = Thread.currentThread().getName()
        try:
            if len(name) > 0:
                raise Refused(name)
            return "unnamed"
        except Refused:
            return name

    @Get(value="/blocking", produces="text/plain")
    @ExecuteOn(TaskExecutors.BLOCKING)
    def blocking(self) -> str:
        return Thread.currentThread().getName()


@Prototype
@Controller("/prototype")
class PrototypeController:
    @Get(value="/plain", produces="text/plain")
    def plain(self) -> str:
        return Thread.currentThread().getName()


# a singleton module: its bean, created when the application starts, injects [module_clock]
Singleton()


@ExecuteOn(TaskExecutors.BLOCKING)
@Get(value="/module/blocking", produces="text/plain")
def module_blocking() -> str:
    return Thread.currentThread().getName()


@Get(value="/module/plain", produces="text/plain")
def module_plain() -> str:
    return Thread.currentThread().getName()


module_clock: Annotated[Clock, Inject]


@Get(value="/module/service", produces="text/plain")
def module_service() -> str:
    return module_clock.thread()
'''

    static final Map PROPERTIES = [
        // Python code does not run on virtual threads: the blocking executor uses platform threads
        'micronaut.executors.blocking.type'   : 'cached',
        'micronaut.executors.blocking.virtual': false,
    ]

    @Unroll
    void "a Python route annotated with @ExecuteOn runs on the executor it names (mode #m)"() {
        given:
        mode = m
        ApplicationContext context = buildContext(SOURCE, true, PROPERTIES)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL).toBlocking()

        expect:
        client.retrieve('/threads/blocking').startsWith('blocking-')
        client.retrieve('/module/blocking').startsWith('blocking-')
        m == StaticCompilationMode.OFF || compiled('ThreadController.blocking')

        cleanup:
        context?.close()

        where:
        m << [StaticCompilationMode.OFF, StaticCompilationMode.ALL]
    }

    void "a compiled route that never enters Python stays on the event loop, an interpreted one runs on io"() {
        when:
        Map<StaticCompilationMode, Map<String, String>> threads = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            ApplicationContext context = buildContext(SOURCE, true, PROPERTIES)
            try {
                def server = context.getBean(EmbeddedServer)
                server.start()
                def client = context.createBean(HttpClient, server.URL).toBlocking()
                threads[m] = ['plain', 'sibling', 'python-sibling', 'service', 'plain-object', 'lazy-raise', 'eager-raise', 'blocking'].collectEntries { String route ->
                    [(route): client.retrieve("/threads/$route".toString())]
                } + ['module/plain': client.retrieve('/module/plain'), 'module/blocking': client.retrieve('/module/blocking'),
                     'module/service': client.retrieve('/module/service'), 'prototype': client.retrieve('/prototype/plain'),
                     'introspected': client.retrieve('/threads/introspected', Map).thread as String,
                     'introspected-read': client.retrieve('/threads/introspected-read')]
                if (m == StaticCompilationMode.ALL) {
                    ['ThreadController.plain', 'ThreadController.sibling', 'ThreadController.named', 'ThreadController.python_sibling',
                     'ThreadController.service', 'Clock.thread', 'module_plain', 'ThreadController.introspected',
                     'ThreadController.plain_object', 'ThreadController.lazy_raise', 'ThreadController.eager_raise', 'ThreadController.introspected_read',
                     'module_service', 'ThreadController.created_on', 'PrototypeController.plain'].each { compiled(it) }
                }
            } finally {
                context.close()
            }
        }
        def off = threads[StaticCompilationMode.OFF]
        def all = threads[StaticCompilationMode.ALL]

        then: "interpreted, every route runs on io but the ones naming the blocking executor"
        off.findAll { route, thread -> !route.contains('blocking') }.every { route, thread -> thread.startsWith('io-') }
        off['blocking'].startsWith('blocking-') && off['module/blocking'].startsWith('blocking-')

        and: "compiled, a route calling Java, a compiled sibling or a compiled service, or building an introspected object, or raising and catching an exception class created lazily, stays on the event loop"
        ['plain', 'sibling', 'service', 'module/plain', 'module/service', 'introspected', 'introspected-read', 'lazy-raise'].every { onEventLoop(all[it]) }

        and: "a route calling a sibling through the Python object, or creating a Python object, an exception included, or of a bean created per use, runs on io, and @ExecuteOn is honoured"
        all['prototype'].startsWith('io-')
        all['python-sibling'].startsWith('io-')
        all['eager-raise'].startsWith('io-')
        all['plain-object'].startsWith('io-')
        all['blocking'].startsWith('blocking-') && all['module/blocking'].startsWith('blocking-')
    }

    void "micronaut.python.static.event-loop false runs a compiled route that never enters Python on io"() {
        given:
        mode = StaticCompilationMode.ALL
        ApplicationContext context = buildContext(SOURCE, true, PROPERTIES + ['micronaut.python.static.event-loop': false])
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL).toBlocking()

        expect: "the routes the default keeps on the event loop run on io, as interpreted ones do"
        compiled('ThreadController.plain') && compiled('module_plain')
        client.retrieve('/threads/plain').startsWith('io-')
        client.retrieve('/module/plain').startsWith('io-')
        client.retrieve('/threads/introspected', Map).thread.startsWith('io-')

        and: "@ExecuteOn is still honoured"
        client.retrieve('/threads/blocking').startsWith('blocking-')

        cleanup:
        context?.close()
    }

    void "a compiled route reading attributes fixed at construction finds its bean created and its slots primed on its first request"() {
        given: "the application started, no request made"
        mode = StaticCompilationMode.ALL
        decisions.clear()
        ApplicationContext context = buildContext(SOURCE, true, PROPERTIES)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL).toBlocking()
        def controller = getBean(context, 'python.ThreadController')
        Map<String, Object> primed = slots(controller)

        expect: "the controller was created at startup, and the bodies read [clock] and [created] from slots it filled for its Python object then"
        compiled('ThreadController.service') && compiled('ThreadController.created_on')
        primed.size() == 2
        primed.values().every { it != null && component(it, 'owner').is(controller.asPolyglotValue()) && component(it, 'held') }

        when: "the first requests of the routes reading them"
        String service = client.retrieve('/threads/service')
        List<String> created = client.retrieve('/threads/created').split('\\|') as List

        then: "they run on the event loop, the controller was not created there, and they read the slots as primed: no read reached the Python object"
        onEventLoop(service)
        !created[0].toLowerCase(Locale.ROOT).contains('eventloop')
        onEventLoop(created[1])
        slots(controller).every { name, fixed -> fixed.is(primed[name]) }

        cleanup:
        context?.close()
    }

    void "a module route reading an injected attribute of the module reads a Java field and stays on the event loop"() {
        given:
        mode = StaticCompilationMode.ALL
        decisions.clear()
        ApplicationContext context = buildContext(SOURCE, true, PROPERTIES)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL).toBlocking()

        expect: "the module was injected at startup: the field the compiled body reads holds the bean before the first request"
        compiled('module_service')
        injected(context, 'injected_module_clock').is(getBean(context, 'python.Clock'))

        and: "the route runs on the event loop, reading the field"
        onEventLoop(client.retrieve('/module/service'))

        cleanup:
        context?.close()
    }

    /**
     * What the slots of the attributes fixed at construction of a stub keep, by slot.
     */
    private static Map<String, Object> slots(Object stub) {
        Map<String, Object> found = [:]
        for (Class<?> type = stub.getClass(); type != null; type = type.superclass) {
            type.declaredFields.findAll { it.type.name == 'io.micronaut.context.python.PythonFixedAttributes$Slot' }.each { slot ->
                slot.accessible = true
                def value = slot.get(stub)
                def current = value == null ? null : value.getClass().getDeclaredField('current')
                current?.accessible = true
                found[slot.name] = current?.get(value)
            }
        }
        return found
    }

    /**
     * The value of a field of the generated class of a module holding an injected attribute, read
     * from the instance of the class without creating its bean.
     */
    private static Object injected(ApplicationContext context, String name) {
        def definition = context.allBeanDefinitions.find { candidate -> candidate.beanType.declaredFields.any { it.name == name } }
        def module = definition.beanType.getMethod('getInstance').invoke(null)
        def field = module.getClass().getDeclaredField(name)
        field.accessible = true
        return field.get(module)
    }

    private static Object component(Object record, String name) {
        def field = record.getClass().getDeclaredField(name)
        field.accessible = true
        return field.get(record)
    }

    private static boolean onEventLoop(String thread) {
        // the Netty event loop, default-eventLoopGroup-N-M
        assert thread.toLowerCase(Locale.ROOT).contains('eventloop'), thread
        return true
    }

    private boolean compiled(String name) {
        def decision = decisions.find { it.qualifiedName() == name }
        assert decision?.outcome() == StaticCompilationDecision.Outcome.COMPILED: "${decision?.qualifiedName()}: ${decision?.reasons()*.message()}"
        return true
    }
}
