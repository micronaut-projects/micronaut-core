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

import javax.tools.JavaFileObject

class PropertyGetterSourceSpec extends GeneratedJavaSourceSpec {

    void "read-only #type @property generates its getter once (#decorators)"() {
        given:
        def pythonCode = """\
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

${decorators}
class Observation:
    tool: str
    error: str | None = None
    @property
    def ok(self) -> ${type}:
        return ${value}
"""

        when:
        def source = generatedSource(pythonCode, "python.Observation")

        then:
        countOccurrences(source, " ${getter}() {") == 1

        where:
        decorators                  | type   | value                | getter
        '@dataclass'                | 'bool' | 'self.error is None' | 'isOk'
        '@dataclass'                | 'str'  | 'self.tool'          | 'getOk'
        '@Introspected\n@dataclass' | 'bool' | 'self.error is None' | 'isOk'
        '@Introspected'             | 'bool' | 'self.error is None' | 'isOk'
        ''                          | 'bool' | 'self.error is None' | 'isOk'
    }

    private static String generatedSource(String pythonCode, String fqcn) {
        def outputs = compile(pythonCode)
        def uriSuffix = '/' + fqcn.replace('.', '/') + '.java'
        def target = outputs.find {
            it.getKind() == JavaFileObject.Kind.SOURCE && it.toUri().toString().endsWith(uriSuffix)
        }
        assert target != null : "Generated source not found for ${fqcn}"
        return target.getCharContent(true).toString()
    }

    private static int countOccurrences(String text, String snippet) {
        int count = 0
        int index = text.indexOf(snippet)
        while (index >= 0) {
            count++
            index = text.indexOf(snippet, index + snippet.length())
        }
        return count
    }
}
