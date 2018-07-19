/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.inject.visitor

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.utils.AstAnnotationUtils

class CustomVisitorSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        ControllerGetVisitor.VISITED_ELEMENTS = []
        AllElementsVisitor.VISITED_ELEMENTS = []
        AllClassesVisitor.VISITED_ELEMENTS = []
        InjectVisitor.VISITED_ELEMENTS = []
        AstAnnotationUtils.invalidateCache()
    }

    void "test class is visited by custom visitor"() {
        buildBeanDefinition('test.TestController', '''
package test

import io.micronaut.http.annotation.*
import javax.inject.Inject

@Controller("/test")
class TestController {

    @Inject private String privateField
    protected String protectedField  
    public String publicField
    @groovy.transform.PackageScope String packagePrivateField
    String property
    
    TestController(String constructorArg) {}
    
    @Inject
    void setterMethod(String method) {}
    
    @Get("/getMethod")
    String getMethod(String argument) {
        ""
    }
    
    @Post("/postMethod")
    String postMethod() {
        ""
    }

}
''')
        expect:
        ControllerGetVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
        AllElementsVisitor.VISITED_ELEMENTS.toSet() == ["test.TestController", "<init>", "privateField", "protectedField", "publicField", "packagePrivateField", "property", "setterMethod", "getMethod", "postMethod"].toSet()
        AllClassesVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
    }

    void "test non controller class is not visited by custom visitor"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*
import javax.inject.Inject

public class TestController {

    @Inject private String privateField
    protected String protectedField  
    public String publicField
    @groovy.transform.PackageScope String packagePrivateField
    String property
    
    
    TestController(String constructorArg) {}
    
    void setterMethod(String method) {}
    
    @Get("/getMethod")
    String getMethod(String argument) {
        ""
    }
    
    @Post("/postMethod")
    String postMethod() {
        ""
    }

}
''')
        expect:
        ControllerGetVisitor.VISITED_ELEMENTS == []
        AllElementsVisitor.VISITED_ELEMENTS == []
        AllClassesVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
        InjectVisitor.VISITED_ELEMENTS == ["test.TestController", "privateField"]
    }
}
