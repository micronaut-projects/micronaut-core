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
package io.micronaut.python.annotation.processing.test.junit

import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.python.compiler.PyronautCompiler
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.EngineFilter
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import spock.lang.Specification

/**
 * Runs compiled Python {@code @MicronautTest} classes with the JUnit platform launcher.
 */
class PythonJunitTestSpec extends Specification {

    private static final String MATH_SERVICE = '''
from abc import ABC, abstractmethod


class MathService(ABC):

    @abstractmethod
    def compute(self, num: int) -> int:
        ...
'''

    private static final String MATH_SERVICE_IMPL = '''
from jakarta.inject import Singleton

from .MathService import MathService


@Singleton
class MathServiceImpl(MathService):

    def compute(self, num: int) -> int:
        return num * 4
'''

    private static final String MATH_CONTROLLER = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

from .MathService import MathService


@Singleton
class MathController:

    def __init__(self, math_service: MathService):
        self.math_service = math_service

    @Executable
    def compute(self, number: int) -> int:
        return self.math_service.compute(number)
'''

    void "test a MockBean factory method returning a Python class replaces the bean for Python consumers"() {
        given:
        def sources = [
            "example/MathService.py": MATH_SERVICE,
            "example/MathServiceImpl.py": MATH_SERVICE_IMPL,
            "example/MathController.py": MATH_CONTROLLER,
            "example/MockBeanCollaboratorTest.py": '''
from typing import Annotated

from io.micronaut.python.annotation.processing.test.junit import CompiledTestContextBuilder
from jakarta.inject import Inject
from micronaut.test.annotation import MockBean
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .MathController import MathController
from .MathService import MathService
from .MathServiceImpl import MathServiceImpl


class MockBeanMathService(MathService):

    def __init__(self):
        self.result = 0
        self.calls: list[int] = []

    def compute(self, num: int) -> int:
        self.calls.append(num)
        return self.result


@MicronautTest(contextBuilder=CompiledTestContextBuilder)
class MockBeanCollaboratorTest:

    math_service: Annotated[MathService, Inject]

    controller: Annotated[MathController, Inject]

    @Test
    def test_compute_num_to_square(self):
        for num, square in [(2, 4), (3, 9)]:
            self.math_service.result = num * num

            result = self.controller.compute(num)

            assert square == result, f"expected {square} but the controller computed {result}"
            assert num in self.math_service.calls, f"the mock was not called with {num}: {self.math_service.calls}"

    @MockBean(MathServiceImpl)
    def math_service_mock(self) -> MockBeanMathService:
        return MockBeanMathService()
'''
        ]

        when:
        def summary = compileAndRun(sources, "example.MockBeanCollaboratorTest")

        then:
        summary.testsFoundCount == 1
        summary.testsSucceededCount == 1
        summary.testsFailedCount == 0
        summary.containersFailedCount == 0
    }

    void "test a MockBean factory method returning a Python abstract base class replaces the bean for Python consumers"() {
        given:
        def sources = [
            "example/MathService.py": MATH_SERVICE,
            "example/MathServiceImpl.py": MATH_SERVICE_IMPL,
            "example/MathController.py": MATH_CONTROLLER,
            "example/AbstractMockBeanCollaboratorTest.py": '''
from typing import Annotated

from io.micronaut.python.annotation.processing.test.junit import CompiledTestContextBuilder
from jakarta.inject import Inject
from micronaut.test.annotation import MockBean
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .MathController import MathController
from .MathService import MathService
from .MathServiceImpl import MathServiceImpl


class RecordingMathService(MathService):

    def __init__(self):
        self.calls: list[int] = []

    def compute(self, num: int) -> int:
        self.calls.append(num)
        return num * num


@MicronautTest(contextBuilder=CompiledTestContextBuilder)
class AbstractMockBeanCollaboratorTest:

    math_service: Annotated[MathService, Inject]

    controller: Annotated[MathController, Inject]

    @Test
    def test_compute_num_to_square(self):
        assert self.controller.compute(3) == 9
        assert self.math_service.calls == [3], f"the mock was not called: {self.math_service.calls}"

    @MockBean(MathServiceImpl)
    def math_service_mock(self) -> MathService:
        return RecordingMathService()
'''
        ]

        when:
        def summary = compileAndRun(sources, "example.AbstractMockBeanCollaboratorTest")

        then:
        summary.testsFoundCount == 1
        summary.testsSucceededCount == 1
        summary.testsFailedCount == 0
        summary.containersFailedCount == 0
    }

    void "test a Nested Python test class runs in the context of the enclosing MicronautTest"() {
        given:
        def sources = [
            "example/OrderService.py": '''
from jakarta.inject import Singleton


@Singleton
class OrderService:

    def place(self, item: str) -> str:
        return "placed " + item
''',
            "example/OrderServiceTest.py": '''
from typing import Annotated

from io.micronaut.python.annotation.processing.test.junit import CompiledTestContextBuilder
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Nested, Test

from .OrderService import OrderService


@MicronautTest(contextBuilder=CompiledTestContextBuilder)
class OrderServiceTest:

    order_service: Annotated[OrderService, Inject]

    @Test
    def test_places_an_order(self):
        assert self.order_service.place("book") == "placed book"

    @Nested
    class Placing:

        order_service: Annotated[OrderService, Inject]

        @Test
        def test_places_an_order_from_the_nested_test(self):
            assert self.order_service.place("pen") == "placed pen"
'''
        ]

        when:
        def summary = compileAndRun(sources, "example.OrderServiceTest")

        then:
        summary.testsFoundCount == 2
        summary.testsSucceededCount == 2
        summary.testsFailedCount == 0
        summary.containersFailedCount == 0
    }

    private static TestExecutionSummary compileAndRun(Map<String, String> sources, String... testClassNames) {
        def sourceDir = File.createTempDir("python-junit-src", "")
        def targetDir = File.createTempDir("python-junit-classes", "")
        try {
            sources.each { path, content ->
                def file = new File(sourceDir, path)
                file.parentFile.mkdirs()
                file.text = content
            }
            PyronautCompiler.builder()
                .pythonSrc(sourceDir.absolutePath)
                .targetDir(targetDir)
                .build()
                .compile()
            def classLoader = new URLClassLoader([targetDir.toURI().toURL()] as URL[], PythonJunitTestSpec.classLoader)
            def thread = Thread.currentThread()
            def previousClassLoader = thread.contextClassLoader
            thread.contextClassLoader = classLoader
            PythonContextRuntime.resetContext()
            try {
                def request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(testClassNames.collect { DiscoverySelectors.selectClass(classLoader.loadClass(it)) })
                    .filters(EngineFilter.includeEngines("junit-jupiter"))
                    .build()
                def listener = new SummaryGeneratingListener()
                LauncherFactory.create().execute(request, listener)
                def summary = listener.summary
                if (!summary.failures.isEmpty()) {
                    def writer = new StringWriter()
                    summary.printFailuresTo(new PrintWriter(writer), 30)
                    System.err.println(writer)
                }
                return summary
            } finally {
                thread.contextClassLoader = previousClassLoader
                PythonContextRuntime.resetContext()
            }
        } finally {
            sourceDir.deleteDir()
            targetDir.deleteDir()
        }
    }
}
