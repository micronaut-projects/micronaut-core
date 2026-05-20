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
import tools.jackson.databind.ObjectMapper

class ConfigurationJsonSchemaSpec extends AbstractPythonTypeElementSpec {

    void "test simple configuration properties schema"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import ConfigurationProperties

@ConfigurationProperties("foo.bar")
class MyProps:
    host: str
    port: int
'''
        def tempDir = File.createTempDir("pyronaut-config-schema", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()
        def schemaFile = new File(tempDir, "META-INF/micronaut-configuration-schemas/python.MyProps.json")
        def schema = new ObjectMapper().readValue(schemaFile.text, Map)

        then:
        schema.'$schema' == "https://json-schema.org/draft/2020-12/schema"
        schema.'$id' == "urn:micronaut:config:python.MyProps"
        schema.title == "python.MyProps"
        schema.'x-micronaut'.prefix == "foo.bar"
        schema.get("properties").host.type == "string"
        schema.get("properties").port.type == "integer"

        cleanup:
        tempDir?.deleteDir()
    }
}
