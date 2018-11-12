package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import org.junit.Ignore

class OpenApiInheritedPojoControllerSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    void "test build OpenAPI doc for POJO with inheritance"() {

        given: "An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import com.fasterxml.jackson.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.micronaut.http.MediaType;



@Controller("/pets")
interface PetOperations {

    /**
     * @param name The person's name
     * @return The greeting
     */
    @Get(uri = "/cases/{name}", produces = MediaType.TEXT_PLAIN)
    Cat getCat(String name);
    
        /**
     * @param name The person's name
     * @return The greeting
     */
    @Get(uri = "/dogs/{name}", produces = MediaType.TEXT_PLAIN)
    Dog getDog(String name);
}

class Dog extends Pet {

    private String breed;

    public void setBreed(String breed) {
        breed = breed;
    }

    public String getBreed() {
        return breed;
    }
}

class Cat extends Pet {

    private int clawSize;

    public void setClawSize(int clawSize) {
        clawSize = clawSize;
    }

    public int getClawSize() {
        return clawSize;
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.PROPERTY, use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = Cat.class, name = "Cat"),
        @JsonSubTypes.Type(value = Dog.class, name = "Dog") })
class Pet {
    @javax.validation.constraints.Min(18)
    private int age;

    private String name;

    public void setAge(int a) {
        age = a;
    }

    /**
     * The age
     */
    public int getAge() {
        return age;
    }

    public void setName(String n) {
        name = n;
    }

    @javax.validation.constraints.Size(max = 30)
    public String getName() {
        return name;
    }
}

@javax.inject.Singleton
class MyBean {}
''')
        then: "the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when: "The OpenAPI is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Schema petSchema = openAPI.components.schemas['Pet']
        Schema catSchema = openAPI.components.schemas['Cat']
        Schema dogSchema = openAPI.components.schemas['Dog']

        then: "the components are valid"
        petSchema != null
        dogSchema != null
        catSchema != null
        !(petSchema instanceof ComposedSchema)
        catSchema instanceof ComposedSchema
        dogSchema instanceof ComposedSchema
        catSchema.type == 'object'
        catSchema.properties.size() == 1
        catSchema.properties['clawSize'].type == 'integer'
        petSchema.type == 'object'
        petSchema.properties.size() == 2

        ((ComposedSchema)catSchema).allOf.size() == 1
        ((ComposedSchema)catSchema).allOf[0].$ref == '#/components/schemas/Pet'
    }


    void "test build OpenAPI doc for POJO with custom annotated inheritance"() {

        given: "An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import com.fasterxml.jackson.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.micronaut.http.MediaType;



@Controller("/pets")
interface PetOperations {

    /**
     * @param name The person's name
     * @return The greeting
     */
    @Get(uri = "/cases/{name}", produces = MediaType.TEXT_PLAIN)
    Cat getCat(String name);
    
    /**
     * @param name The person's name
     * @return The greeting
     */
    @Get(uri = "/dogs/{name}", produces = MediaType.TEXT_PLAIN)
    Dog getDog(String name);    
}

@Schema(description = "Dog", allOf = { Pet.class })
class Dog extends Pet {

    private String breed;

    public void setBreed(String breed) {
        breed = breed;
    }

    public String getBreed() {
        return breed;
    }
}

@Schema(description = "Cat", allOf = { Pet.class })
class Cat extends Pet {

    private int clawSize;

    public void setClawSize(int clawSize) {
        clawSize = clawSize;
    }

    public int getClawSize() {
        return clawSize;
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.PROPERTY, use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = Cat.class, name = "Cat"),
        @JsonSubTypes.Type(value = Dog.class, name = "Dog") })
@Schema(description = "Pet Desc")
class Pet {
    @javax.validation.constraints.Min(18)
    private int age;

    private String name;

    public void setAge(int a) {
        age = a;
    }

    /**
     * The age
     */
    public int getAge() {
        return age;
    }

    public void setName(String n) {
        name = n;
    }

    @javax.validation.constraints.Size(max = 30)
    public String getName() {
        return name;
    }
}

@javax.inject.Singleton
class MyBean {}
''')
        then: "the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when: "The OpenAPI is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Schema petSchema = openAPI.components.schemas['Pet']
        Schema catSchema = openAPI.components.schemas['Cat']
        Schema dogSchema = openAPI.components.schemas['Dog']

        then: "the components are valid"
        petSchema != null
        dogSchema != null
        catSchema != null
        !(petSchema instanceof ComposedSchema)
        catSchema instanceof ComposedSchema
        dogSchema instanceof ComposedSchema
        catSchema.type == 'object'
        catSchema.properties.size() == 1
        catSchema.properties['clawSize'].type == 'integer'
        petSchema.description == 'Pet Desc'
        petSchema.type == 'object'
        petSchema.properties.size() == 2

        ((ComposedSchema)catSchema).allOf.size() == 1
        ((ComposedSchema)catSchema).allOf[0].$ref == '#/components/schemas/Pet'
        ((ComposedSchema)dogSchema).allOf.size() == 1
        ((ComposedSchema)dogSchema).allOf[0].$ref == '#/components/schemas/Pet'
    }



}
