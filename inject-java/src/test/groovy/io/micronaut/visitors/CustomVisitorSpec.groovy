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
    @Inject protected String protectedField;   
    @Inject public String publicField;
    @Inject String packagePrivateField;
    
    @Inject
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
        AllElementsVisitor.VISITED_ELEMENTS == ["test.TestController", "<init>", "privateField", "protectedField", "publicField", "packagePrivateField", "setterMethod", "getMethod", "postMethod"]
        AllClassesVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
        InjectVisitor.VISITED_ELEMENTS == ["test.TestController", "<init>", "privateField", "protectedField", "publicField", "packagePrivateField", "setterMethod"]
    }

    void "test non controller class is not visited by custom visitor"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@javax.inject.Singleton
public class TestController {


    @Inject private String privateField;  
    @Inject protected String protectedField;   
    @Inject public String publicField;
    @Inject String packagePrivateField;
    
    @Inject
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
        ControllerGetVisitor.VISITED_ELEMENTS == []
        AllElementsVisitor.VISITED_ELEMENTS == []
        AllClassesVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
        InjectVisitor.VISITED_ELEMENTS == ["test.TestController", "<init>", "privateField", "protectedField", "publicField", "packagePrivateField", "setterMethod"]
    }
}
