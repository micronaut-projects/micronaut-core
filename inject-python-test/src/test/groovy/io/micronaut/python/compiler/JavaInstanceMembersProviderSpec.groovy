/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.compiler

import io.micronaut.context.python.PythonContextExecutor
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class JavaInstanceMembersProviderSpec extends AbstractPythonTypeElementSpec {

    private static final String PROVIDER = 'io.micronaut.context.python.PythonJavaInstanceMembersProvider'
    private static final String SOURCE = '''
from jakarta.inject import Singleton
from java.lang import Object
from micronaut.context.python.annotation import TypeChecked
from micronaut.http import HttpRequest
from micronaut.python.compiler import InstanceAliasFixture

class PythonControl:
    def getPort(self) -> int:
        return 17

@Singleton
class Caller:
    def identity(self, value: PythonControl) -> PythonControl:
        return value

    @TypeChecked
    def object_name(self) -> str:
        return Object().get_class().getName()

    @TypeChecked
    def deep(self) -> str:
        return InstanceAliasFixture().middle().leaf().value

    def call(self) -> str:
        request = HttpRequest.GET("/registered")
        request.basic_auth("user", "pass")
        return str(request.get_uri()) + ":" + request.getHeaders().get("Authorization")

    def fixture(self) -> str:
        fixture = InstanceAliasFixture()
        assert fixture.ready
        assert not hasattr(fixture, "url")
        assert not hasattr(fixture, "get_url")
        assert not hasattr(fixture, "get_static_value")
        assert not hasattr(fixture, "mixed_value")
        assert not hasattr(fixture, "void")
        assert not hasattr(fixture, "wrong")
        assert fixture.with_value("x") == fixture.withValue("x") == "text:x"
        assert fixture.with_value(7) == "number:7"
        assert fixture.http2_status == fixture.get_http2_status() == "HTTP2"
        assert fixture.with__value() == "underscores"
        try:
            fixture.name = "Grace"
            raise AssertionError("property should be read-only")
        except AttributeError:
            pass
        assert fixture.name == "Ada"
        return str(fixture.get_http_status().add("alias")) + fixture.literal_value("-exact")
'''

    def "the compiler registers instance aliases and they work through a referenced interface"() {
        given:
        def target = File.createTempDir('python-instance-members', '')
        def compiler = PyronautCompiler.builder().pythonCode(SOURCE).targetDir(target).build()

        when:
        compiler.compile()
        def services = new File(target, 'META-INF/micronaut/' + PROVIDER).list() as List

        then:
        services != null
        services.size() == 1
        services[0].contains('PythonJavaInstanceMembers$')

        when:
        def loader = new URLClassLoader([target.toURI().toURL()] as URL[], getClass().classLoader)
        def providers = SoftServiceLoader.load(loader.loadClass(PROVIDER), loader).collectAll()
        def entries = providers.collectMany { it.entries() }

        then:
        entries.any { it.type().name == 'io.micronaut.http.HttpRequest' }
        entries.any { it.type().name == 'io.micronaut.http.MutableHttpRequest' }
        entries.any { it.type() == InstanceAliasFixture.Leaf }
        !entries.any { it.type().name == 'python.PythonControl' }
        !entries.any { it.type().name == 'io.micronaut.http.simple.SimpleHttpRequest' }
        providers[0].entries().is(providers[0].entries())

        when: "an optional method return type is missing at runtime"
        def optionalLoader = new OptionalApiClassLoader(target.toURI().toURL(), getClass().classLoader)
        def optionalProviders = SoftServiceLoader.load(optionalLoader.loadClass(PROVIDER), optionalLoader).collectAll()
        def available = optionalProviders.collectMany { it.entries() }

        then:
        optionalProviders.size() == 1
        available.any { it.type() == InstanceAliasFixture }
        !available.any { it.type().name == 'java.util.StringJoiner' }

        cleanup:
        optionalLoader?.close()
        loader?.close()
        target.deleteDir()
    }

    def "generated aliases retain concrete host dispatch in primary and pooled contexts"() {
        given:
        def context = buildContext(SOURCE, false, ['micronaut.python.pool.enabled': true, 'micronaut.python.pool.size': 1])
        def caller = getBean(context, 'python.Caller')

        expect:
        caller.call() == '/registered:Basic dXNlcjpwYXNz'
        caller.fixture() == 'alias-exact'
        caller.deep() == 'deep'
        caller.object_name() == 'java.lang.Object'
        context.getBean(PythonContextExecutor).withContext { pooled ->
            pooled.eval('python', '''
from micronaut.http import HttpRequest
request = HttpRequest.GET("/pooled")
request.basic_auth("user", "pass")
str(request.get_uri()) + ":" + request.getHeaders().get("Authorization")
''').asString()
        } == '/pooled:Basic dXNlcjpwYXNz'

        cleanup:
        context?.close()
    }

    def "plain Python-only sources do not register Object aliases"() {
        given:
        def target = File.createTempDir('python-no-instance-members', '')

        when:
        PyronautCompiler.builder().targetDir(target).pythonCode('''
from jakarta.inject import Singleton

@Singleton
class Plain:
    def value(self) -> int:
        return 1
''').build().compile()

        then:
        !new File(target, 'META-INF/micronaut/' + PROVIDER).exists()

        cleanup:
        target.deleteDir()
    }

    private static final class OptionalApiClassLoader extends URLClassLoader {
        OptionalApiClassLoader(URL url, ClassLoader parent) {
            super([url] as URL[], parent)
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name == 'java.util.StringJoiner') {
                throw new ClassNotFoundException(name)
            }
            return super.loadClass(name, resolve)
        }
    }
}
