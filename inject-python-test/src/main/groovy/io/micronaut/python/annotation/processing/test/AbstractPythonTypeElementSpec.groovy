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

import groovy.transform.CompileStatic
import io.micronaut.inject.ast.ClassElement
import io.micronaut.python.compiler.PyronautCompiler
import spock.lang.Specification

/**
 * Base class to extend from to allow compilation of Python sources
 * at runtime to allow testing of compile time behavior.
 *
 * @author Micronaut
 * @since 4.8.0
 */
abstract class AbstractPythonTypeElementSpec extends Specification {

    /**
     * Builds a class element for the given Python source code.
     * @param pythonCode The Python source code
     * @param closure the callback
     * @return The class element
     */
    <T> T buildClassElement(String pythonCode, Closure<T> closure) {
        List<ClassElement> capturedElements = []
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("inject-python-test/src/test/java")
            .classElementCallback { ClassElement classElement ->
                capturedElements.add(classElement)
            }
            .build()

        compiler.buildClassLoader()

        // Return the first captured element to the closure
        def element = capturedElements ? capturedElements[0] : null
        if (element && closure) {
            return closure.call(element)
        }
        return null
    }
}
