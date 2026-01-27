package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class PooledClassSpec extends AbstractPythonTypeElementSpec {

    void "pooled class yields multiple context ids and map/list conversions"() {
        given:
        def python = '''
from jakarta.inject import Singleton as S
from micronaut.http.annotation import Controller, Get
from micronaut.context.python.scope import Pooled

@Pooled
@S
class CtxReader:

    def get_ctx_id(self) -> str:
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
        Map<String, Object> props = ["micronaut.python.pool.size": 4, "micronaut.python.pool.sync-init": true]
        ApplicationContext context = buildContext(python, true, props)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        when:
        def ids = (1..12).collect { client.toBlocking().retrieve("/classpool/ctx") }.toSet()

        then:
        ids.size() >= 2

        when:
        def mapJson = client.toBlocking().retrieve("/classpool/map")
        def listJson = client.toBlocking().retrieve("/classpool/list")

        then:
        mapJson == '{"k":"v"}'
        listJson == '["a","b"]'

        cleanup:
        client?.close()
        context?.close()
    }

    void "pooled class with ctor args fails compilation"() {
        given:
        def python = '''
from micronaut.context.python.scope import Pooled

@Pooled
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
