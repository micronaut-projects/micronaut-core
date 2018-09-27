package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.security.SecurityScheme

class OpenApiOperationCallbackSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    void "test parse the OpenAPI @Operation annotation with @Callback"() {
        given:
        buildBeanDefinition('test.MyBean', '''

package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.parameters.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.callbacks.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;

@Controller("/")
class MyController {

    @Post("/test")
    @Callback(
            callbackUrlExpression = "http://$request.query.url",
            name = "subscription",
            operation = {
                    @Operation(
                            method = "post",
                            description = "payload data will be sent",
                            parameters = {
                                    @Parameter(in = ParameterIn.PATH, name = "subscriptionId", required = true, schema = @Schema(
                                            type = "string",
                                            format = "uuid",
                                            description = "the generated UUID",
                                            accessMode = Schema.AccessMode.READ_ONLY
                                    ))
                            },
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Return this code if the callback was received and processed successfully"
                                    ),
                                    @ApiResponse(
                                            responseCode = "205",
                                            description = "Return this code to unsubscribe from future data updates"
                                    ),
                                    @ApiResponse(
                                            responseCode = "default",
                                            description = "All other response codes will disable this callback subscription"
                                    )
                            }),
                    @Operation(
                            method = "get",
                            description = "payload data will be received"
                    ),
                    @Operation(
                            method = "put",
                            description = "payload data will be sent"
                    )})
    @Operation(description = "subscribes a client to updates relevant to the requestor's account, as " +
            "identified by the input token.  The supplied url will be used as the delivery address for response payloads")
    public SubscriptionResponse subscribe(@Schema(required = true, description = "the authentication token " +
            "provided after initially authenticating to the application") @Header("x-auth-token") String token,
                                          @Schema(required = true, description = "the URL to call with response " +
                                                  "data") @QueryValue("url") String url) {
        return null;
    }

}
class SubscriptionResponse {
        private String subscriptionUuid;
}

@javax.inject.Singleton
class MyBean {}
''')

        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Operation operation = openAPI.paths?.get("/test")?.post

        expect:
        operation
        operation.description.startsWith('subscribes')
        operation.parameters.size() == 2
        operation.parameters[0].name == 'token'
        operation.parameters[0].in == 'header'
        operation.parameters[0].required
        operation.parameters[1].name == 'url'
        operation.parameters[1].in == 'query'
        operation.parameters[1].required
        operation.parameters[1].schema.description.contains("the URL")
        operation.parameters[0].schema.description.contains("the authentication token")
        operation.callbacks
        operation.callbacks['subscription']
        operation.callbacks['subscription']['http://$request.query.url']
        operation.callbacks['subscription']['http://$request.query.url'].get
        operation.callbacks['subscription']['http://$request.query.url'].post
        operation.callbacks['subscription']['http://$request.query.url'].put
        operation.callbacks['subscription']['http://$request.query.url'].post.description == 'payload data will be sent'
        operation.callbacks['subscription']['http://$request.query.url'].post.parameters.size() == 1
        operation.callbacks['subscription']['http://$request.query.url'].post.parameters[0].name == 'subscriptionId'
        operation.callbacks['subscription']['http://$request.query.url'].post.parameters[0].schema.description == 'the generated UUID'
        operation.callbacks['subscription']['http://$request.query.url'].post.parameters[0].schema.format == 'uuid'
//        operation.callbacks['subscription']['http://$request.query.url'].post.parameters[0].schema.readOnly

    }
}
