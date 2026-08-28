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
package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class InjectScopeSpec extends AbstractPythonTypeElementSpec {

    void "test inject scope closes constructor and method injected beans"() {
        given:
        def context = buildContext('''
from typing import Annotated

from jakarta.annotation import PreDestroy
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Bean, Executable, InjectScope

class Connection:
    def is_open(self) -> bool:
        return False

    def is_other_open(self) -> bool:
        return False

@Bean
class Other:
    def __init__(self):
        self.open = True

    @PreDestroy
    def close(self):
        self.open = False

@Bean
class TestConnection(Connection):
    def __init__(self, other: Other):
        self.other = other
        self.open = True

    def is_open(self) -> bool:
        return self.open and self.other.open

    def is_other_open(self) -> bool:
        return self.other.open

    @PreDestroy
    def close(self):
        self.open = False

@Singleton
class Test:
    def __init__(self, conn1: Annotated[Connection, InjectScope], conn2: Annotated[Connection, InjectScope]):
        assert conn1.is_open()
        assert conn2.is_open()
        self.created_connections = [conn1, conn2]

    @Inject
    def init(self, conn3: Annotated[Connection, InjectScope]):
        assert conn3.is_open()
        self.created_connections.append(conn3)

    @Executable
    def created_connection_count(self) -> int:
        return len(self.created_connections)

    @Executable
    def all_connections_closed(self) -> bool:
        return all(not conn.is_open() for conn in self.created_connections)

    @Executable
    def all_other_connections_closed(self) -> bool:
        return all(not conn.is_other_open() for conn in self.created_connections)
''')

        when:
        def bean = getBean(context, "python.Test")

        then:
        bean.created_connection_count() == 3
        bean.all_connections_closed()
        bean.all_other_connections_closed()

        cleanup:
        context?.close()
    }
}
