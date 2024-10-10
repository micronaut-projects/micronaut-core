/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import spock.lang.Shared

class InternalVisitor2Spec extends AbstractTypeElementSpec {

    @Shared
    def parser = new JavaParser()

    void "test warning"() {
        when:
            def bd = buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;

@Controller("/test")
class TestController extends io.micronaut.visitors.AbstractInternalMethodClass {

    @Get("/getMethod")
    public String getMethod(String argument) {
        return "";
    }

    @Post("/postMethod")
    public String postMethod() {
        return "";
    }

    @Override
    public String foo() {
        return super.foo();
    }

}
''')
            def diagnostics = parser.diagnosticCollector.diagnostics
        then:
            bd
            diagnostics.size() == 2
            diagnostics[0].toString().contains("test/TestController.java:20: warning: Element extends or implements an internal or experimental Micronaut API")
            diagnostics[1].toString().contains("warning: Overriding an internal Micronaut API may result in breaking changes in minor or patch versions of the framework. Proceed with caution!")
    }

    @Override
    protected JavaParser newJavaParser() {
        return parser
    }
}
