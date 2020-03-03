/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.validation.internal

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class InteralApiRuleSpec extends AbstractTypeElementSpec {

    void "test missing parameter"() {
        given:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.out = new PrintStream(out)

        when:
        buildTypeElement("""

package test;

import io.micronaut.core.io.scan.*;

class Foo extends CachingClassPathAnnotationScanner {

}

""")
        String output = out.toString("UTF8")

        then:
        noExceptionThrown()
        output.contains("warning: Element extends or implements an internal or experimental Micronaut API\n" +
                "class Foo extends CachingClassPathAnnotationScanner {")
        output.contains("Overriding an internal Micronaut API may result in breaking changes in minor or patch versions")

        cleanup:
        System.out = oldOut
    }

}