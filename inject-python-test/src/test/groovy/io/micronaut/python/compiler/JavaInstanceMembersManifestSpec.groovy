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
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class JavaInstanceMembersManifestSpec extends AbstractPythonTypeElementSpec {

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

    def "the compiler records the instance member names of the referenced Java types in the Java imports manifest"() {
        given:
        def target = File.createTempDir('python-instance-members', '')

        when:
        PyronautCompiler.builder().pythonCode(SOURCE).targetDir(target).build().compile()
        def members = JavaImportsManifest.read(target).instanceMembers

        then: "imports, their method return types and the receivers the checker resolved in a deeper chain"
        members['io.micronaut.http.HttpRequest']['uri'] == ['getUri', 'property']
        members['io.micronaut.http.MutableHttpRequest']['basic_auth'] == ['basicAuth', 'method']
        members['java.lang.Object']['get_class'] == ['getClass', 'method']
        !members['java.lang.Object'].containsKey('class')
        members[InstanceAliasFixture.Leaf.name]['value'] == ['getValue', 'property']

        and: "neither Python classes, implementations nor the types GraalPy converts to Python values"
        !members.containsKey('python.PythonControl')
        !members.containsKey('io.micronaut.http.simple.SimpleHttpRequest')
        !members.containsKey('java.lang.String')

        and: "an ambiguous name, or a method with static overloads, is blocked; a static method has no name"
        def fixture = members[InstanceAliasFixture.name]
        fixture['ready'] == ['isReady', 'property']
        fixture['with_value'] == ['withValue', 'method']
        fixture['http_status'] == ['getHTTPStatus', 'property']
        fixture['http2_status'] == ['getHTTP2Status', 'property']
        fixture['get_http2_status'] == ['getHTTP2Status', 'method']
        fixture['with__value'] == ['with__Value', 'method']
        fixture['get_void'] == ['getVoid', 'method']
        fixture['url'] == []
        fixture['get_url'] == []
        fixture['mixed_value'] == []
        !fixture.containsKey('void')
        !fixture.containsKey('wrong')
        !fixture.containsKey('static_value')
        !fixture.containsKey('get_static_value')

        cleanup:
        target.deleteDir()
    }

    def "the registered names retain concrete host dispatch in primary and pooled contexts"() {
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

    def "plain Python sources record no instance member names"() {
        given:
        def target = File.createTempDir('python-no-instance-members', '')

        when:
        PyronautCompiler.builder().targetDir(target).pythonCode('''
from jakarta.inject import Singleton

@Singleton
class Plain:
    def value(self) -> int:
        return 1

    def text(self, value: str) -> str:
        return value
''').build().compile()

        then:
        JavaImportsManifest.read(target).instanceMembers.isEmpty()

        cleanup:
        target.deleteDir()
    }
}
