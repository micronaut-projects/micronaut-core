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
package io.micronaut.inject.visitor

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.utils.AstAnnotationUtils

class CustomVisitorSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        ControllerGetVisitor.clearVisited()
        AllElementsVisitor.clearVisited()
        AllClassesVisitor.clearVisited()
        InjectVisitor.clearVisited()
    }

    void cleanup() {
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
        ControllerGetVisitor.getVisited() == ["test.TestController", "getMethod"]
        AllElementsVisitor.getVisited().toSet() == ["test.TestController","privateField", "protectedField", "publicField", "packagePrivateField", "property", "setterMethod", "getMethod", "postMethod"].toSet()
        AllClassesVisitor.getVisited() == ["test.TestController", "getMethod"]
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
        ControllerGetVisitor.getVisited().empty
        AllElementsVisitor.getVisited().empty
        AllClassesVisitor.getVisited() == ["test.TestController", "getMethod"]
        InjectVisitor.getVisited() == ["test.TestController", "privateField"]
    }

    void "test @Generated class is not visited by any visitor"() {
        buildBeanDefinition('test.TestGenerated', '''
package test;

import io.micronaut.core.annotation.Generated
import javax.inject.Inject

@Generated
public class TestGenerated {

    @Inject private String privateField
    protected String protectedField  
    public String publicField
    @groovy.transform.PackageScope String packagePrivateField
    String property
    
    
    TestGenerated(String constructorArg) {}
    
    void setterMethod(String method) {}

}
''')
        expect:
        ControllerGetVisitor.getVisited().empty
        AllElementsVisitor.getVisited().empty
        AllClassesVisitor.getVisited().empty
        InjectVisitor.getVisited().empty
    }
}
