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
 * name each route returns tells where it ran.
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
from java.lang import Thread
from micronaut.http.annotation import Controller, Get
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn


@Controller("/threads")
class ThreadController:

    @Get(value="/blocking", produces="text/plain")
    @ExecuteOn(TaskExecutors.BLOCKING)
    def blocking(self) -> str:
        return Thread.currentThread().getName()


@ExecuteOn(TaskExecutors.BLOCKING)
@Get(value="/module/blocking", produces="text/plain")
def module_blocking() -> str:
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

    private boolean compiled(String name) {
        def decision = decisions.find { it.qualifiedName() == name }
        assert decision?.outcome() == StaticCompilationDecision.Outcome.COMPILED: "${decision?.qualifiedName()}: ${decision?.reasons()*.message()}"
        return true
    }
}
