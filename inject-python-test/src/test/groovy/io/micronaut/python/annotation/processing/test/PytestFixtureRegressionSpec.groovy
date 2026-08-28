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
package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler

/**
 * Regression test for AttributeError: 'Call' object has no attribute 'attr'
 * when encountering decorators like @pytest.fixture (ast.Call whose func is ast.Attribute).
 */
class PytestFixtureRegressionSpec extends AbstractPythonTypeElementSpec {

    def "decorators with attribute-call form do not crash transformer"() {
        given:
        String pythonCode = '''
import pytest
from pyronaut.test import *
from micronaut.runtime.server import EmbeddedServer

@pytest.fixture
def my_context(request):
    fixture = micronaut_test_fixture(request, MicronautTest(environments=["foo"], transactional=False,
                                                            properties={"custom.property": "test_value"}))
    yield fixture
    fixture.stop()

@pytest.fixture(scope="session")
def base_url(my_context):
    server = my_context[EmbeddedServer]
    return f"http://localhost:{server.port}"
'''

        when:
        def compiler = PyronautCompiler.builder().pythonCode(pythonCode).build()
        def cl = compiler.buildClassLoader()

        then:
        cl != null
    }
}
