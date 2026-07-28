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
package io.micronaut.python.compiler

import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject

import spock.lang.Specification

class PythonApplicationMigrationSpec extends Specification {

    void "compilation fails when using old PythonApplication package"() {
        when:
        compileUsingOldFqcn()

        then:
        def e = thrown(RuntimeException)
        e.cause instanceof IllegalArgumentException
        e.cause.message.contains('The argument does not represent an annotation type')
    }

    private static Iterable<JavaFileObject> compileUsingOldFqcn() {
        String source = '''
package pyronaut_application;
import io.micronaut.python.processing.annotation.PythonApplication;
@PythonApplication(code="print('hello')")
class PyronautMain { }
'''.stripIndent()
        JavaFileObject main = new SimpleJavaFileObject(
                java.net.URI.create('string:///pyronaut_application/PyronautMain.java'),
                JavaFileObject.Kind.SOURCE) {
            @Override
            CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source
            }
        }
        def jc = new PyronautJavaCompiler()
        return jc.compileInMemory([main] as JavaFileObject[], null, null, null, null)
    }
}
