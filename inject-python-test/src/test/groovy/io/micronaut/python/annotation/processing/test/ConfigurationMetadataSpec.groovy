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

import io.micronaut.python.compiler.PyronautCompiler
import spock.lang.PendingFeature
import tools.jackson.databind.ObjectMapper

class ConfigurationMetadataSpec extends AbstractPythonTypeElementSpec {

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0086")
    void "test simple configuration properties metadata"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties

@ConfigurationProperties("foo.bar")
class MyProps:
    host: str
    port: int
'''
        def tempDir = File.createTempDir("pyronaut-config-metadata", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()
        def metadataFile = new File(tempDir, "META-INF/spring-configuration-metadata.json")
        def metadata = new ObjectMapper().readValue(metadataFile.text, Map)

        then:
        metadata.groups == [[name: "foo.bar", type: "python.MyProps"]]
        metadata.properties.contains([name: "foo.bar.host", type: "java.lang.String", sourceType: "python.MyProps"])
        metadata.properties.contains([name: "foo.bar.port", type: "int", sourceType: "python.MyProps"])

        cleanup:
        tempDir?.deleteDir()
    }
}
