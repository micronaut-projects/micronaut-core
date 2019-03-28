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
package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.ArraySchema

class OpenApiOperationParseSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    void "test parse the OpenAPI @ApiResponse Content with @ArraySchema annotation"() {
        given:
        buildBeanDefinition('test.MyBean','''
package test;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.List;

@Controller("/pet")
interface PetOperations {

    @Operation(summary = "List Pets",
            description = "List Pets",
            tags = "pets",
            responses = {
                    @ApiResponse(description = "List Pets", responseCode = "200", content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = Pet.class)))),
                    @ApiResponse(description = "Pets not found", responseCode = "404")
            })
    @Get
    HttpResponse<List<Pet>> list();
    
    @Operation(summary = "List Pet Names",
            description = "List Pet Names",
            tags = "pet-name",
            responses = {
                    @ApiResponse(description = "List Pet Names", responseCode = "200", content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(type="string")))),
                    @ApiResponse(description = "Not found", responseCode = "404")
            })
    @Get("/names")
    HttpResponse<List<String>> listNames();
}

@Schema(description = "Represents a pet")
class Pet {
    @Schema(description = "The pet name")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

@javax.inject.Singleton
class MyBean {}
''')

        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/pet")?.get

        expect:
        operation
        operation.summary == 'List Pets'
        operation.tags.size() == 1
        operation.tags == ['pets']
        operation.responses.size() == 2
        operation.responses.'200'.content.size() == 1
        operation.responses.'200'.content['application/json']
        operation.responses.'200'.content['application/json'].schema
        operation.responses.'200'.content['application/json'].schema.type == "array"
        ((ArraySchema) operation.responses.'200'.content['application/json'].schema).items.$ref == "#/components/schemas/Pet"

        when:
        Operation operationNames = AbstractOpenApiVisitor.testReference?.paths?.get("/pet/names")?.get

        then:
        operationNames
        operationNames.summary == "List Pet Names"
        operationNames.tags == ['pet-name']
        operationNames.responses.size() == 2
        operationNames.responses.'200'.content.size() == 1
        operationNames.responses.'200'.content['application/json']
        operationNames.responses.'200'.content['application/json'].schema
        operationNames.responses.'200'.content['application/json'].schema.type == "array"
        ((ArraySchema) operationNames.responses.'200'.content['application/json'].schema).items.type == "string"
    }

    void "test parse the OpenAPI @ApiResponse Content with @Schema annotation"() {
        given:
        buildBeanDefinition('test.MyBean','''
package test;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Schema(description = "Represents a pet")
class Pet {
    @Schema(description = "The pet name")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

@Controller("/pet")
interface PetOperations {
    @Operation(summary = "Save Pet",
            description = "Save Pet",
            tags = "save-pet",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Save Pet",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Pet.class))),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Page Not Found"
                    )})
    @Post
    HttpResponse<Pet> save(@Body Pet pet);
}


@javax.inject.Singleton
class MyBean {}
''')

        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/pet")?.post

        expect:
        operation
        operation.summary == 'Save Pet'
        operation.tags.size() == 1
        operation.tags == ['save-pet']
        operation.responses.size() == 2
        operation.responses.'200'.content.size() == 1
        operation.responses.'200'.content['application/json']
        operation.responses.'200'.content['application/json'].schema
        operation.responses.'200'.content['application/json'].schema.$ref == "#/components/schemas/Pet"
    }

    void "test parse the OpenAPI @Operation annotation"() {
        given:
        buildBeanDefinition('test.MyBean', '''

package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.parameters.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;

@Controller("/")
class MyController {

  @Put
  @Consumes("application/json")
  @Operation(summary = "Update an existing pet",
          tags = {"pets"},
          security = @SecurityRequirement(
                                  name = "petstore-auth",
                                  scopes = "write:pets"),
          responses = {
                  @ApiResponse(
                     content = @Content(mediaType = "application/json",
                             schema = @Schema(implementation = Pet.class))),
                  @ApiResponse(responseCode = "400", description = "Invalid ID supplied"),
                  @ApiResponse(responseCode = "404", description = "Pet not found"),
                  @ApiResponse(responseCode = "405", description = "Validation exception") }
    )
    public Response updatePet(
      @RequestBody(description = "Pet object that needs to be added to the store", required = true,
                              content = @Content(
                                      schema = @Schema(implementation = Pet.class))) Pet pet) {
        return null;
    }
}

class Pet {}

class Response {}

@javax.inject.Singleton
class MyBean {}
''')

        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/")?.put

        expect:
        operation
        operation.summary == 'Update an existing pet'
        operation.tags.size() == 1
        operation.tags == ['pets']
        operation.security.size() == 1
        operation.security.first().name == ['petstore-auth']
        operation.security.first().scopes == ['write:pets']
        operation.responses.size() == 4
        operation.responses.default.content.size() == 1
        operation.responses.default.content['application/json']
        operation.responses.default.content['application/json'].schema
        operation.responses.'400'.description == 'Invalid ID supplied'

    }


    void "test parse the OpenAPI @Operation annotation with tags and security defined as annotations"() {
        given:
        buildBeanDefinition('test.MyBean', '''

package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.parameters.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.tags.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;

@Controller("/")
class MyController {

  @Put
  @Consumes("application/json")
  @Operation(summary = "Update an existing pet",
          responses = {
                  @ApiResponse(
                     content = @Content(mediaType = "application/json",
                             schema = @Schema(implementation = Pet.class))),
                  @ApiResponse(responseCode = "400", description = "Invalid ID supplied"),
                  @ApiResponse(responseCode = "404", description = "Pet not found"),
                  @ApiResponse(responseCode = "405", description = "Validation exception") }
    )
    @Tag(name = "pets")
    @SecurityRequirement(
          name = "petstore-auth",
          scopes = "write:pets")
    public Response updatePet(
      @RequestBody(description = "Pet object that needs to be added to the store", required = true,
                              content = @Content(
                                      schema = @Schema(implementation = Pet.class))) Pet pet) {
        return null;
    }
}

class Pet {}

class Response {}

@javax.inject.Singleton
class MyBean {}
''')

        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/")?.put

        expect:
        operation
        operation.summary == 'Update an existing pet'
        operation.tags.size() == 1
        operation.tags == ['pets']
        operation.security.size() == 1
        operation.security.first().name == ['petstore-auth']
        operation.security.first().scopes == ['write:pets']
        operation.responses.size() == 4
        operation.responses.default.content.size() == 1
        operation.responses.default.content['application/json']
        operation.responses.default.content['application/json'].schema
        operation.responses.'400'.description == 'Invalid ID supplied'

    }

    void "test parse the OpenAPI @Operation annotation with @ApiResponse on method"() {
        given:
        buildBeanDefinition('test.MyBean', '''

package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.parameters.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;

@Controller("/")
class MyController {

  @Put
  @Consumes("application/json")
  @Operation(summary = "Update an existing pet",
          tags = {"pets"},
          security = @SecurityRequirement(
                                  name = "petstore-auth",
                                  scopes = "write:pets")
    )
    @ApiResponse(
                     content = @Content(mediaType = "application/json",
                             schema = @Schema(implementation = Pet.class)))
      @ApiResponse(responseCode = "400", description = "Invalid ID supplied")
      @ApiResponse(responseCode = "404", description = "Pet not found")
      @ApiResponse(responseCode = "405", description = "Validation exception")    
    public Response updatePet(
      @RequestBody(description = "Pet object that needs to be added to the store", required = true,
                              content = @Content(
                                      schema = @Schema(implementation = Pet.class))) Pet pet) {
        return null;
    }
}

class Pet {}

class Response {}

@javax.inject.Singleton
class MyBean {}
''')

        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/")?.put

        expect:
        operation
        operation.summary == 'Update an existing pet'
        operation.tags.size() == 1
        operation.tags == ['pets']
        operation.security.size() == 1
        operation.security.first().name == ['petstore-auth']
        operation.security.first().scopes == ['write:pets']
        operation.responses.size() == 4
        operation.responses.default.content.size() == 1
        operation.responses.default.content['application/json']
        operation.responses.default.content['application/json'].schema
        operation.responses.'400'.description == 'Invalid ID supplied'

    }
}
