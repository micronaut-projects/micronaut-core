package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PooledClassSpec extends AbstractPythonTypeElementSpec {

    void "pooled class rotates directly and injects into target context"() {
        given:
        def python = '''
from jakarta.inject import Singleton as S
from micronaut.http.annotation import Controller, Get
from micronaut.context.python.scope import ContextPooled

@ContextPooled
@S
class CtxReader:

    def get_ctx_id(self) -> str:
        import time
        time.sleep(0.1)
        import builtins
        g = globals()
        return g.get("__MN_CTX_ID__") or builtins.__dict__.get("__MN_CTX_ID__") or "unknown"
    def get_map(self) -> dict[str, str]:
        return { 'k': 'v' }
    def get_list(self) -> list[str]:
        return ['a','b']

@Controller("/classpool")
class PoolController:
    def __init__(self, reader: CtxReader):
        self.reader = reader

    @Get("/ctx")
    def ctx(self) -> str:
        return self.reader.get_ctx_id()

    @Get("/map")
    def m(self) -> dict[str, str]:
        return self.reader.get_map()

    @Get("/list")
    def l(self) -> list[str]:
        return self.reader.get_list()
        '''
        def previousContextIdProperty = System.getProperty("micronaut.python.context-id.enabled")
        System.setProperty("micronaut.python.context-id.enabled", "true")
        Map<String, Object> props = ["micronaut.python.pool.size": 4]
        ApplicationContext context = buildContext(python, true, props)
        def readerClass = context.classLoader.loadClass("python.CtxReader")
        def reader = context.getBean(readerClass)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)
        def executor = Executors.newFixedThreadPool(4)
        warmPool(context, executor, 4)

        when:
        def getCtxId = readerClass.getMethod("get_ctx_id")
        def ids = (1..4).collect {
            executor.submit { getCtxId.invoke(reader) }
        }.collect {
            it.get(5, TimeUnit.SECONDS)
        }.toSet()

        then:
        ids.size() >= 2

        when:
        def responseIds = (1..4).collect { client.toBlocking().retrieve("/classpool/ctx") }.toSet()

        then:
        responseIds.size() == 1

        when:
        def mapJson = client.toBlocking().retrieve("/classpool/map")
        def listJson = client.toBlocking().retrieve("/classpool/list")

        then:
        mapJson == '{"k":"v"}'
        listJson == '["a","b"]'

        cleanup:
        executor?.shutdownNow()
        client?.close()
        context?.close()
        if (previousContextIdProperty == null) {
            System.clearProperty("micronaut.python.context-id.enabled")
        } else {
            System.setProperty("micronaut.python.context-id.enabled", previousContextIdProperty)
        }
    }

    void "pooled class with ctor args fails compilation"() {
        given:
        def python = '''
from micronaut.context.python.scope import ContextPooled

@ContextPooled
class Bad:
    def __init__(self, a: int):
        self.a = a
'''
        when:
        buildContext(python, false)

        then:
        def ex = thrown(Exception)
        ex.message.contains("must be stateless")
    }
}
