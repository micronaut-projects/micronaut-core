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

class PythonAsyncBridgeSpec extends GeneratedJavaSourceSpec {

    void "async method bridge returns completion stage of awaited result type"() {
        expect:
        assertGeneratedSourceContains('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class AsyncService:
    @Executable
    async def message(self) -> str:
        return "ok"
''', '''
public CompletionStage<String> message() {
''')
    }

    void "async method bridge converts coroutine result through asyncio runtime"() {
        expect:
        assertGeneratedSourceContains('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class AsyncService:
    @Executable
    async def message(self) -> str:
        return "ok"
''', '''
return (CompletionStage<String>) PythonAsyncioRuntime.toCompletionStage(pythonCoroutine);
''')
    }

    void "abstract async client method bridge returns completion stage of awaited result type"() {
        expect:
        assertGeneratedSourceContains('''
from abc import ABC, abstractmethod
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client

@Client("/backend")
class BackendClient(ABC):
    @Get("/message")
    @abstractmethod
    async def message(self) -> str:
        ...
''', '''
public CompletionStage<String> message() {
''')
    }

    void "classless async route bridge converts coroutine result through asyncio runtime"() {
        expect:
        assertGeneratedSourceContains('''
from micronaut.http.annotation import Get

@Get("/message")
async def message() -> str:
    return "ok"
''', '''
return (CompletionStage<String>) PythonAsyncioRuntime.toCompletionStage(pythonCoroutine);
''')
    }

    void "async constructors are rejected"() {
        expect:
        assertCompilationFailsContaining('''
from jakarta.inject import Singleton

@Singleton
class BadService:
    async def __init__(self):
        pass
''', 'Async constructors are not supported')
    }

    void "async properties are rejected"() {
        expect:
        assertCompilationFailsContaining('''
from jakarta.inject import Singleton

@Singleton
class BadService:
    @property
    async def message(self) -> str:
        return "bad"
''', 'Async property [message] is not supported')
    }
}
