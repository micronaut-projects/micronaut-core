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
from jakarta.inject import Singleton
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


@Singleton
class Clock:
    def thread(self) -> str:
        return Thread.currentThread().getName()


@Controller("/threads")
class ThreadController:
    def __init__(self, clock: Clock):
        self.clock = clock

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

    @Get("/introspected")
    def introspected(self) -> Stamp:
        return Stamp(Thread.currentThread().getName(), 1)

    @Get(value="/plain-object", produces="text/plain")
    def plain_object(self) -> str:
        name = Thread.currentThread().getName()
        Plain(name)
        return name

    @Get(value="/blocking", produces="text/plain")
    @ExecuteOn(TaskExecutors.BLOCKING)
    def blocking(self) -> str:
        return Thread.currentThread().getName()


@ExecuteOn(TaskExecutors.BLOCKING)
@Get(value="/module/blocking", produces="text/plain")
def module_blocking() -> str:
    return Thread.currentThread().getName()


@Get(value="/module/plain", produces="text/plain")
def module_plain() -> str:
    return Thread.currentThread().getName()
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
                threads[m] = ['plain', 'sibling', 'python-sibling', 'service', 'plain-object', 'blocking'].collectEntries { String route ->
                    [(route): client.retrieve("/threads/$route".toString())]
                } + ['module/plain': client.retrieve('/module/plain'), 'module/blocking': client.retrieve('/module/blocking'),
                     'introspected': client.retrieve('/threads/introspected', Map).thread as String]
                if (m == StaticCompilationMode.ALL) {
                    ['ThreadController.plain', 'ThreadController.sibling', 'ThreadController.named', 'ThreadController.python_sibling',
                     'ThreadController.service', 'Clock.thread', 'module_plain', 'ThreadController.introspected',
                     'ThreadController.plain_object'].each { compiled(it) }
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

        and: "compiled, a route calling Java, a compiled sibling or a compiled service, or building an introspected object, stays on the event loop"
        ['plain', 'sibling', 'service', 'module/plain', 'introspected'].every { onEventLoop(all[it]) }

        and: "a route calling a sibling through the Python object, or creating a Python object, runs on io, and @ExecuteOn is honoured"
        all['python-sibling'].startsWith('io-')
        all['plain-object'].startsWith('io-')
        all['blocking'].startsWith('blocking-') && all['module/blocking'].startsWith('blocking-')
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
