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
package io.micronaut.visitors

import io.micronaut.inject.AbstractTypeElementSpec

class CustomVisitorSpec extends AbstractTypeElementSpec {

    void setup() {
        ControllerGetVisitor.VISITED_ELEMENTS = []
        AllElementsVisitor.VISITED_ELEMENTS = []
        AllClassesVisitor.VISITED_ELEMENTS = []
        InjectVisitor.VISITED_ELEMENTS = []
    }

    void "test class is visited by custom visitor"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController {

    @Inject private String privateField;  
    protected String protectedField;   
    public String publicField;
    String packagePrivateField;
    
    TestController(String constructorArg) {}
    
    @Inject
    void setterMethod(String method) {}
    
    @Get("/getMethod")
    public String getMethod(String argument) {
        return "";
    }
    
    @Post("/postMethod")
    public String postMethod() {
        return "";
    }

}
''')
        expect:
        ControllerGetVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
        AllElementsVisitor.VISITED_ELEMENTS == ["test.TestController", "privateField", "protectedField", "publicField", "packagePrivateField",  "setterMethod", "getMethod", "postMethod"]
        AllClassesVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
        AllElementsVisitor.FINISH_COUNT.get() == 1
    }

    void "test non controller class is not visited by custom visitor"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

public class TestController {

    @Inject private String privateField;  
    protected String protectedField;   
    public String publicField;
    String packagePrivateField;
    
    TestController(String constructorArg) {}
    
    void setterMethod(String method) {}
    
    @Get("/getMethod")
    public String getMethod(String argument) {
        return "";
    }
    
    @Post("/postMethod")
    public String postMethod() {
        return "";
    }

}
''')
        expect:
        ControllerGetVisitor.VISITED_ELEMENTS == []
        AllElementsVisitor.VISITED_ELEMENTS == []
        AllClassesVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
        InjectVisitor.VISITED_ELEMENTS == ["test.TestController", "privateField"]
    }

    void "test @Generated class is not visited by any visitor"() {
        buildBeanDefinition('test.TestGenerated', '''
package test;

import io.micronaut.core.annotation.Generated;
import javax.inject.Inject;

@Generated
public class TestGenerated {

    @Inject private String privateField;  
    protected String protectedField;   
    public String publicField;
    String packagePrivateField;
    
    TestGenerated(String constructorArg) {}
    
    void setterMethod(String method) {}

}
''')
        expect:
        ControllerGetVisitor.VISITED_ELEMENTS == []
        AllElementsVisitor.VISITED_ELEMENTS == []
        AllClassesVisitor.VISITED_ELEMENTS == []
        InjectVisitor.VISITED_ELEMENTS == []
    }
}
