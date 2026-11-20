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
package io.micronaut.python.annotation.processing.test

import io.micronaut.runtime.context.scope.refresh.RefreshScope

class FactoryFieldOfScopedProxySpec extends AbstractPythonTypeElementSpec {

    void "test a bean attribute of a factory behind a scoped proxy is read from the proxy's target"() {
        given:
        ScopedProxyInitCounter.reset()
        def context = buildContext('''
from typing import Annotated
import java
from jakarta.annotation import PostConstruct
from micronaut.context.annotation import Bean, Factory, Prototype
from micronaut.runtime.context.scope import Refreshable

Counter = java.type("io.micronaut.python.annotation.processing.test.ScopedProxyInitCounter")

class Reading:
    def __init__(self, source: str):
        self.source = source

    def get_source(self) -> str:
        return self.source

@Factory
@Refreshable
class TestFactory:
    reading: Annotated[Reading, Bean, Prototype] = None

    @PostConstruct
    def created(self):
        Counter.increment()
        self.reading = Reading("instance " + str(Counter.count()))
''', true)

        expect: "the attribute is read from the factory instance the scope holds, not from its proxy"
        getBean(context, 'python.Reading').get_source() == 'instance 1'

        when:
        context.getBean(RefreshScope).refresh()

        then: "the attribute is read from the instance created after the refresh"
        getBean(context, 'python.Reading').get_source() == 'instance 2'

        cleanup:
        context?.close()
    }
}
