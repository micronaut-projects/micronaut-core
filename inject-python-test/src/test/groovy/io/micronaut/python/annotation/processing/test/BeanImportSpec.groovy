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

import io.micronaut.python.annotation.processing.test.beanimport.ImportedPackageBean
import io.micronaut.python.annotation.processing.test.beanimport.UpstreamByteConstructorBean
import spock.lang.PendingFeature

class BeanImportSpec extends AbstractPythonTypeElementSpec {

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0025")
    void "test bean import with byte array constructor"() {
        given:
        def context = buildContext('''
import java

from jakarta.inject import Named
from micronaut.context.annotation import Bean, Factory, Import

UpstreamByteConstructorBean = java.type("io.micronaut.python.annotation.processing.test.beanimport.UpstreamByteConstructorBean")

@Import(classes=[UpstreamByteConstructorBean])
class Application:
    pass

@Factory
class BytesFactory:
    @Bean
    @Named("some-bytes")
    def my_bytes(self) -> bytes:
        return "test".encode("utf-8")
''')

        expect:
        context.getBean(UpstreamByteConstructorBean).toString() == "test"

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0026")
    void "test bean import for classes"() {
        given:
        def context = buildContext('''
import java

from micronaut.context.annotation import Import

ImportedPackageBean = java.type("io.micronaut.python.annotation.processing.test.beanimport.ImportedPackageBean")

@Import(classes=[ImportedPackageBean])
class Application:
    pass
''')

        expect:
        context.containsBean(ImportedPackageBean)

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0027")
    void "test bean import for package"() {
        given:
        def context = buildContext('''
from micronaut.context.annotation import Import

@Import(packages=["io.micronaut.python.annotation.processing.test.beanimport"])
class Application:
    pass
''')

        expect:
        context.containsBean(ImportedPackageBean)

        cleanup:
        context?.close()
    }
}
