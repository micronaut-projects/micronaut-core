package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import spock.lang.Specification

class OpenApiPublicFieldsSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    def cleanup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "")
    }

    void "test that public fields can also be used to define schema"() {

        given:"An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.*;
import com.fasterxml.jackson.annotation.*;
/**
 * @author graemerocher
 * @since 1.0
 */

@Controller("/pets")
interface PetOperations<T extends Pet> {

    @Post("/")
    Single<T> save(String name, int age);
}

class Pet {
    public int age;
    
    // ignored by json
    @JsonIgnore
    public int ignored;
    // hidden by swagger
    @Hidden
    public int hidden;
    
    // private should not be included
    private String name;
    
    // protected should not be included
    protected String protectme;
    
    // static should not be included
    public static String CONST;
}
@javax.inject.Singleton
class MyBean {}
''')
        then:"the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when:"The OpenAPI is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Schema petSchema = openAPI.components.schemas['Pet']

        then:"the components are valid"
        petSchema.type == 'object'
        petSchema.properties.size() == 1
        petSchema.properties['age'].type == 'integer'

        when:"the /pets path is retrieved"
        PathItem pathItem = openAPI.paths.get("/pets")

        then:"it is included in the OpenAPI doc"
        pathItem.post.operationId == 'save'
        pathItem.post.requestBody
        pathItem.post.requestBody.required
        pathItem.post.requestBody.content
        pathItem.post.requestBody.content.size() == 1
        pathItem.post.requestBody.content['application/json'].schema
        pathItem.post.requestBody.content['application/json'].schema.type == 'object'
        pathItem.post.requestBody.content['application/json'].schema.properties.size() == 2
        pathItem.post.requestBody.content['application/json'].schema.properties['name'].type == 'string'
        !pathItem.post.requestBody.content['application/json'].schema.properties['name'].nullable
    }
}
