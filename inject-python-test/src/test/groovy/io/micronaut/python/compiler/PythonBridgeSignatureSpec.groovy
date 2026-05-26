/*
 * Copyright 2017-2025 original authors
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

class PythonBridgeSignatureSpec extends GeneratedJavaSourceSpec {

    void "bridge method uses resolved inherited generic interface signature"() {
        expect:
        assertGeneratedSourceContains('''
from jakarta.inject import Singleton
from io.micronaut.python.compiler import (
    GenericAuthenticationRequest,
    GenericRequest,
    GenericRequestAuthenticationProvider,
)


@Singleton
class PythonAuthenticationProvider(GenericRequestAuthenticationProvider):
    def authenticate(
        self,
        request_context: GenericRequest,
        auth_request: GenericAuthenticationRequest,
    ) -> str:
        return "ok"
''', '''
public String authenticate(GenericRequest<Object> request_context, GenericAuthenticationRequest<String, String> auth_request) {
''')
    }

    void "bridge method keeps resolved HttpRequest generic signature"() {
        expect:
        assertGeneratedSourceContains('''
from jakarta.inject import Singleton
from micronaut.http import HttpRequest
from io.micronaut.python.compiler import (
    GenericAuthenticationRequest,
    GenericHttpRequestAuthenticationProvider,
)


@Singleton
class PythonHttpAuthenticationProvider(GenericHttpRequestAuthenticationProvider):
    def authenticate(
        self,
        request_context: HttpRequest,
        auth_request: GenericAuthenticationRequest,
    ) -> str:
        return "ok"
''', '''
public String authenticate(HttpRequest<Object> request_context, GenericAuthenticationRequest<String, String> auth_request) {
''')
    }

    void "bridge method preserves resolved wildcard generic signature"() {
        expect:
        assertGeneratedSourceContains('''
from jakarta.inject import Singleton
from java.util import List
from io.micronaut.python.compiler import (
    GenericAuthenticationRequest,
    GenericWildcardAuthenticationProvider,
)


@Singleton
class PythonWildcardAuthenticationProvider(GenericWildcardAuthenticationProvider):
    def authenticate(
        self,
        request_context: List,
        auth_request: GenericAuthenticationRequest,
    ) -> str:
        return "ok"
''', '''
public String authenticate(List<?> request_context, GenericAuthenticationRequest<String, String> auth_request) {
''')
    }

    void "bridge method keeps raw generic parameter declared by Java interface"() {
        expect:
        assertGeneratedSourceContains('''
from jakarta.inject import Singleton
from java.lang import RuntimeException
from io.micronaut.python.compiler import (
    GenericRawRequestHandler,
    GenericRequest,
)


@Singleton
class PythonRawRequestHandler(GenericRawRequestHandler[RuntimeException]):
    def handle(
        self,
        request_context: GenericRequest,
        exception: RuntimeException,
    ) -> str:
        return "ok"
''', '''
public String handle(GenericRequest request_context, RuntimeException exception) {
''')
    }

    void "bridge methods keep repository method type variables"() {
        String source = '''
from io.micronaut.core.annotation import Introspected
from io.micronaut.python.compiler import GenericRoleRepository


@Introspected
class PythonRoleRepository(GenericRoleRepository):
    pass
'''

        expect:
        assertGeneratedSourceContains(source, '''
public <S extends GenericRole> List<S> saveAll(Iterable<S> entities) {
''')
        assertGeneratedSourceContains(source, '''
public <S extends GenericRole> S save(S entity) {
    Value pythonResult = GraalPyRuntimeUtil.invokePythonMethod
''')
        assertGeneratedSourceContains(source, '''
return GraalPyRuntimeUtil.asObject((Object) GraalPyRuntimeUtil.convertValue(pythonResult, io.micronaut.python.compiler.GenericRole.class));
''')
        assertGeneratedSourceContains(source, '''
public <S extends GenericRole> List<S> updateAll(Iterable<S> entities) {
''')
        assertGeneratedSourceContains(source, '''
public void deleteAll(Iterable<? extends GenericRole> entities) {
''')
    }
}
