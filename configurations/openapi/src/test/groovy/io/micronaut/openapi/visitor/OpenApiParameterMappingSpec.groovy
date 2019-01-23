package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema

class OpenApiParameterMappingSpec extends AbstractTypeElementSpec {

    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    def cleanup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "")
    }

    void "test that @Parameter propagates correctly"() {

        given:"An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.*;
import java.util.List;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.parameters.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import com.fasterxml.jackson.annotation.*;
/**
 * @author graemerocher
 * @since 1.0
 */

@Controller("/networks")
interface NetworkOperations {

    /**
     * @param fooBar some other description
     */
   @Operation(
            summary = "Gets mappings from TTT using vod provider mappings",
            description = "Migration of /networks endpoint from TTT. Gets mappings from XYZ using provider mappings",
            responses = {
                    @ApiResponse(
                            responseCode = "200", description = "Successfully got abc data from TTT",
                            content = {
                                    @Content(
                                            mediaType = "application/json", schema = @Schema(implementation = Greeting.class)
                                    )
                            }
                    )
            })
    @Get
    public HttpResponse<Greeting> getNetworks(
            @Parameter(
                    name = "fooBar",
                    description = "NA/true/false (case insensitive)",
                    required = false,
                    schema = @Schema(
                            nullable = true,
                            type = "string", allowableValues = {"NA", "true", "false"},
                            defaultValue = "NA",
                            example = "NA"
                    )
            )
            @QueryValue(value = "fooBar", defaultValue = "NA") String fooBar
    );
}

class Greeting {
    public String message;
}
@javax.inject.Singleton
class MyBean {}
''')
        then:"the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when:"The OpenAPI is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Schema greetingSchema = openAPI.components.schemas['Greeting']

        then:"the components are valid"
        greetingSchema.type == 'object'
        greetingSchema.properties.size() == 1
        greetingSchema.properties['message'].type == 'string'

        when:"the /pets path is retrieved"
        PathItem pathItem = openAPI.paths.get("/networks")

        then:"it is included in the OpenAPI doc"
        pathItem.get.operationId == 'getNetworks'
        pathItem.get.parameters.size() == 1
        pathItem.get.parameters[0].name =='fooBar'
        pathItem.get.parameters[0].description == 'NA/true/false (case insensitive)'
        !pathItem.get.parameters[0].required
        pathItem.get.parameters[0].schema.type == 'string'
        pathItem.get.parameters[0].schema.default == 'NA'
        pathItem.get.parameters[0].schema.enum == ['NA', 'true', 'false']
        pathItem.get.parameters[0].schema.example == 'NA'
    }

    void "test that @Parameter elements can be hidden on interface"() {

        given:"An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.*;
import java.util.List;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.parameters.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import com.fasterxml.jackson.annotation.*;
/**
 * @author graemerocher
 * @since 1.0
 */

@Controller("/networks")
interface NetworkOperations {

    @Get
    public HttpResponse<Greeting> getNetworks(
            @Parameter(hidden=true) java.security.Principal auth,
            @QueryValue(value = "fooBar", defaultValue = "NA") String fooBar
    );
}

class Greeting {
    public String message;
}
@javax.inject.Singleton
class MyBean {}
''')
        then:"the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when:"The OpenAPI is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Schema greetingSchema = openAPI.components.schemas['Greeting']

        then:"the components are valid"
        greetingSchema.type == 'object'
        greetingSchema.properties.size() == 1
        greetingSchema.properties['message'].type == 'string'

        when:"the /pets path is retrieved"
        PathItem pathItem = openAPI.paths.get("/networks")

        then:"it is included in the OpenAPI doc"
        pathItem.get.operationId == 'getNetworks'
        pathItem.get.parameters.size() == 1
        pathItem.get.parameters[0].name =='fooBar'
    }

    void "test that @Parameter elements can be hidden on type"() {

        given:"An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.*;
import java.util.List;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.parameters.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import com.fasterxml.jackson.annotation.*;
/**
 * @author graemerocher
 * @since 1.0
 */

@Controller("/networks")
interface NetworkOperations {

    @Post
    public HttpResponse<Greeting> getNetworks(
            @Parameter(hidden=true) Greeting auth,
            @QueryValue(value = "fooBar", defaultValue = "NA") String fooBar
    );
}

class Greeting {
    public String message;
}
@javax.inject.Singleton
class MyBean {}
''')
        then:"the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when:"The OpenAPI is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Schema greetingSchema = openAPI.components.schemas['Greeting']

        then:"the components are valid"
        greetingSchema.type == 'object'
        greetingSchema.properties.size() == 1
        greetingSchema.properties['message'].type == 'string'

        when:"the /pets path is retrieved"
        PathItem pathItem = openAPI.paths.get("/networks")

        then:"it is included in the OpenAPI doc"
        pathItem.post.operationId == 'getNetworks'
        pathItem.post.parameters.size() == 1
        pathItem.post.parameters[0].name =='fooBar'
    }
}


