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

class OpenApiOperationParseSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
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
