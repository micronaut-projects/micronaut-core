package io.micronaut.inject.visitor

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.utils.AstAnnotationUtils

class CustomVisitorSpec extends AbstractBeanDefinitionSpec {

    void cleanup() {
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
    @Inject protected String protectedField  
    @Inject public String publicField
    @Inject @groovy.transform.PackageScope String packagePrivateField
    @Inject String property
    
    @Inject
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
        InjectVisitor.VISITED_ELEMENTS.toSet() == ["test.TestController", "<init>", "privateField", "protectedField", "publicField", "packagePrivateField", "property", "setterMethod"].toSet()
    }

    void "test non controller class is not visited by custom visitor"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*
import javax.inject.Inject

@javax.inject.Singleton
public class TestController {

    @Inject private String privateField
    @Inject protected String protectedField  
    @Inject public String publicField
    @Inject @groovy.transform.PackageScope String packagePrivateField
    @Inject String property
    
    @Inject
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
        ControllerGetVisitor.VISITED_ELEMENTS == []
        AllElementsVisitor.VISITED_ELEMENTS == []
        AllClassesVisitor.VISITED_ELEMENTS == ["test.TestController", "getMethod"]
        InjectVisitor.VISITED_ELEMENTS.toSet() == ["test.TestController", "<init>", "privateField", "protectedField", "publicField", "packagePrivateField", "property", "setterMethod"].toSet()
    }
}
