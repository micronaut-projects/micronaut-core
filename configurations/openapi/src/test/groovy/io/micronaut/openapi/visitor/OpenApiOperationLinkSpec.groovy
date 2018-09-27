package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation

class OpenApiOperationLinkSpec extends AbstractTypeElementSpec {
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
import io.swagger.v3.oas.annotations.links.*;
import io.micronaut.http.annotation.*;
import java.util.List;

@Controller("/")
class MyController {

    @Put("/")
    @Consumes("application/json")
    @Operation(operationId = "getUser",
        responses = {
                @ApiResponse(description = "test description",
                        content = @Content(mediaType = "*/*", schema = @Schema(ref = "#/components/schemas/User")),
                        links = {
                                @Link(
                                        name = "address",
                                        operationId = "getAddress",
                                        parameters = @LinkParameter(
                                                name = "userId",
                                                expression = "$request.query.userId"))
                        })}
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

        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Operation operation = openAPI.paths?.get("/")?.put

        expect:
        operation
        operation.responses.size() == 1
        operation.responses.default.links.size() == 1
        operation.responses.default.links.address.operationId == 'getAddress'
        operation.responses.default.links.address.parameters.size() == 1
        operation.responses.default.links.address.parameters['userId'] == '$request.query.userId'
    }
}
