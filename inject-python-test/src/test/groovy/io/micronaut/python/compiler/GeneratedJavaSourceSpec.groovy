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

abstract class GeneratedJavaSourceSpec extends Specification {

    protected void assertGeneratedSourceContains(String pythonCode, String expectedSnippet) {
        def outputs = compile(pythonCode)
        def sources = outputs.findAll { it.toUri().toString().contains("/SOURCE_OUTPUT/") && it.getKind() == JavaFileObject.Kind.SOURCE }
        assert !sources.isEmpty()
        def matched = sources.any {
            def javaCode = it.getCharContent(true).toString()
            javaCode.contains(expectedSnippet.stripIndent().trim())
        }
        assert matched : ("None of the generated sources contained the expected snippet. Available sources:\n" +
                sources.collect { it.toUri().toString() + "\n-----------\n" + it.getCharContent(true).toString() + '----------' }.join('\n'))
    }

    protected void assertGeneratedSourceEquals(String pythonCode, String expectedFull, String fqcn = null) {
        def outputs = compile(pythonCode)
        def sources = outputs.findAll { it.toUri().toString().contains("/SOURCE_OUTPUT/") && it.getKind() == JavaFileObject.Kind.SOURCE }
        assert !sources.isEmpty()
        def expected = expectedFull.stripIndent().replace('\r\n','\n').replace('\r','\n').trim()
        if (fqcn != null) {
            def uriSuffix = '/' + fqcn.replace('.', '/') + '.java'
            def target = sources.find { it.toUri().toString().endsWith(uriSuffix) }
            assert target != null : "Generated source not found for ${fqcn}"
            def content = target.getCharContent(true).toString().replace('\r\n','\n').replace('\r','\n').trim()
            assert content == expected
        } else {
            def matched = sources.any { it.getCharContent(true).toString().replace('\r\n','\n').replace('\r','\n').trim() == expected }
            assert matched : "No generated source exactly matched expected content"
        }
    }

    private static Iterable<JavaFileObject> compile(String pythonCode) {
        String source = """
package pyronaut_application;
import io.micronaut.context.python.annotation.PythonApplication;
@PythonApplication(code=\"${escape(pythonCode)}\")
class PyronautMain { }
""".stripIndent()
        JavaFileObject main = new SimpleJavaFileObject(
                java.net.URI.create("string:///pyronaut_application/PyronautMain.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source
            }
        }
        def jc = new PyronautJavaCompiler()
        return jc.compileInMemory([main] as JavaFileObject[], null, null, null, null)
    }

    protected static String escape(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }
}
