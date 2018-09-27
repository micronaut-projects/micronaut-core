package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.security.SecurityScheme

class OpenApiSecurityRequirementSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    void "test parse the OpenAPI @Operation annotation with @SecurityScheme for APIKEY"() {
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
@SecurityScheme(name = "myOauth2Security",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER
)
class MyController {

    @Put("/")
    @Consumes("application/json")
    @SecurityRequirement(name = "myOauth2Security", scopes = "write: read")
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
        operation.security.size() == 1
        operation.security.first().name == ['myOauth2Security']
        operation.security.first().scopes == ['write: read']
        openAPI.components.securitySchemes.size() == 1
        openAPI.components.securitySchemes['myOauth2Security'].type == SecurityScheme.Type.APIKEY
        openAPI.components.securitySchemes['myOauth2Security'].in == SecurityScheme.In.HEADER

    }

    void "test parse the OpenAPI @Operation annotation with @SecurityScheme for OAUTH2"() {
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
@SecurityScheme(name = "myOauth2Security",
        type = SecuritySchemeType.OAUTH2,
        flows = @OAuthFlows(
                implicit = @OAuthFlow(authorizationUrl = "http://url.com/auth",
                        scopes = @OAuthScope(name = "write:pets", description = "modify pets in your account"))))
class MyController {

    @Put("/")
    @Consumes("application/json")
    @SecurityRequirement(name = "myOauth2Security", scopes = "write: read")
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
        operation.security.size() == 1
        operation.security.first().name == ['myOauth2Security']
        operation.security.first().scopes == ['write: read']
        openAPI.components.securitySchemes.size() == 1
        openAPI.components.securitySchemes['myOauth2Security'].type == SecurityScheme.Type.OAUTH2
        openAPI.components.securitySchemes['myOauth2Security'].flows
        openAPI.components.securitySchemes['myOauth2Security'].flows.implicit
        openAPI.components.securitySchemes['myOauth2Security'].flows.implicit.authorizationUrl == 'http://url.com/auth'
        openAPI.components.securitySchemes['myOauth2Security'].flows.implicit.scopes
        openAPI.components.securitySchemes['myOauth2Security'].flows.implicit.scopes.size() == 1

    }
}
