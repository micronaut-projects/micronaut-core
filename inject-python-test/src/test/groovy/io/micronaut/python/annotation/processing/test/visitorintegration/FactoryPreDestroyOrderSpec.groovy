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
package io.micronaut.python.annotation.processing.test.visitorintegration

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * The pre-destroy callback named by a factory method ({@code Bean(preDestroy = "stop")}) is bridged on the
 * produced Python class whichever of the factory and the produced class the stub generator visits first.
 */
class FactoryPreDestroyOrderSpec extends AbstractPythonTypeElementSpec {

    void "test a factory declared before the class it produces bridges the pre-destroy callback"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean, Executable, Factory


@Factory
class ConnectionFactory:

    @Bean(preDestroy="stop")
    @Singleton
    def connection(self) -> "Connection":
        return Connection()


class Connection:
    stopped: bool = False

    def stop(self):
        self.stopped = True

    @Executable
    def is_stopped(self) -> bool:
        return self.stopped
''')
        def connection = getBean(context, "python.Connection")

        expect:
        connection.class.getMethod("stop") != null
        !connection.is_stopped()

        when:
        context.destroyBean(connection)

        then:
        connection.is_stopped()

        cleanup:
        context?.close()
    }
}
