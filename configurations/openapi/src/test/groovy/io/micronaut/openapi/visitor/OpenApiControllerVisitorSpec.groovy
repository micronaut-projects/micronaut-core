package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.parameters.Parameter

class OpenApiControllerVisitorSpec extends AbstractTypeElementSpec {

    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    void "test build OpenAPI doc with @Error"() {

        // TODO: currently the @Error is just ignored, consider adding to OpenApi.components.responses in the future
        given:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.*;
import com.fasterxml.jackson.core.*;
import io.micronaut.http.hateoas.*;
import java.util.List;
import javax.validation.constraints.*;

@Controller("/")
class MyController {

    @Get("/subscription/{subscriptionId}")
    public String getSubscription( @Size(min=10, max=20) java.util.List<String> subscriptionId) { 
        return null;                               
     }
     
    @io.micronaut.http.annotation.Error
    public HttpResponse<JsonError> jsonError(HttpRequest request, JsonParseException jsonParseException) { 
        JsonError error = new JsonError("Invalid JSON: " + jsonParseException.getMessage()) 
                .link(Link.SELF, Link.of(request.getUri()));
    
        return HttpResponse.<JsonError>status(HttpStatus.BAD_REQUEST, "Fix Your JSON")
                .body(error); 
    }     
}

@javax.inject.Singleton
class MyBean {}
''')
        when:
        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/subscription/{subscriptionId}")?.get

        then:
        operation != null
        operation.operationId == 'getSubscription'
        operation.parameters.size() == 1


        when:
        def parameter = operation.parameters[0]

        then:
        parameter.in == 'path'
        parameter.schema.maxLength == null
        parameter.schema.minLength == null
        parameter.schema.minItems == 10
        parameter.schema.maxItems == 20
    }

    void "test build OpenAPI doc with request and response"() {

        given:"An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import java.util.List;
import io.micronaut.http.*;
/**
 * @author graemerocher
 * @since 1.0
 */

@Controller("/pets")
interface PetOperations<T extends String> {

    /**
     * List the pets
     *
     * @return a list of pet names
     */
    @Get("/")
    HttpResponse<Single<List<T>>> list();

    /**
     * Find a pet by a slug
     *
     * @param slug The slug name
     * @return A pet or 404
     */
    @Get("/{slug}")
    HttpResponse<T> find(String slug, HttpRequest request);
}


@javax.inject.Singleton
class MyBean {}
''')
        then:"the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when:"the /pets path is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        PathItem pathItem = openAPI.paths.get("/pets")

        then:"it is included in the OpenAPI doc"
        pathItem.get.operationId == 'list'
        pathItem.get.description == 'List the pets'
        pathItem.get.responses['default']
        pathItem.get.responses['default'].description == 'a list of pet names'
        pathItem.get.responses['default'].content['application/json'].schema
        pathItem.get.responses['default'].content['application/json'].schema.type == 'array'

        when:"the /{slug} path is retrieved"
        pathItem = openAPI.paths.get("/pets/{slug}")

        then:"it is included in the OpenAPI doc"
        pathItem.get.description == 'Find a pet by a slug'
        pathItem.get.operationId == 'find'
        pathItem.get.parameters.size() == 1
        pathItem.get.parameters[0].name == 'slug'
        pathItem.get.parameters[0].in == ParameterIn.PATH.toString()
        pathItem.get.parameters[0].required
        pathItem.get.parameters[0].schema
        pathItem.get.parameters[0].description == 'The slug name'
        pathItem.get.parameters[0].schema.type == 'string'
        pathItem.get.responses.size() == 1
        pathItem.get.responses['default'] != null
        pathItem.get.responses['default'].content['application/json'].schema.type == 'string'


    }

    void "test build OpenAPI doc for simple type with generics"() {

        given:"An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import java.util.List;

/**
 * @author graemerocher
 * @since 1.0
 */

@Controller("/pets")
interface PetOperations<T extends String> {

    /**
     * List the pets
     *
     * @return a list of pet names
     */
    @Get("/")
    Single<List<T>> list();

    /**
     * List the pets
     *
     * @return a list of pet names
     */
    @Get("/flowable")
    Flowable<T> flowable();
    
    @Get("/random")
    Maybe<T> random();

    @Get("/vendor/{name}")
    Single<List<T>> byVendor(String name);

    /**
     * Find a pet by a slug
     *
     * @param slug The slug name
     * @return A pet or 404
     */
    @Get("/{slug}")
    Maybe<T> find(String slug);

    @Post("/")
    Single<T> save(@Body T pet);
}


@javax.inject.Singleton
class MyBean {}
''')
        then:"the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when:"the /pets path is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        PathItem pathItem = openAPI.paths.get("/pets")

        then:"it is included in the OpenAPI doc"
        pathItem.get.operationId == 'list'
        pathItem.get.description == 'List the pets'
        pathItem.get.responses['default']
        pathItem.get.responses['default'].description == 'a list of pet names'
        pathItem.get.responses['default'].content['application/json'].schema
        pathItem.get.responses['default'].content['application/json'].schema.type == 'array'
        pathItem.post.operationId == 'save'
        pathItem.post.requestBody
        pathItem.post.requestBody.required
        pathItem.post.requestBody.content
        pathItem.post.requestBody.content.size() == 1


        when:"the /{slug} path is retrieved"
        pathItem = openAPI.paths.get("/pets/{slug}")

        then:"it is included in the OpenAPI doc"
        pathItem.get.description == 'Find a pet by a slug'
        pathItem.get.operationId == 'find'
        pathItem.get.parameters.size() == 1
        pathItem.get.parameters[0].name == 'slug'
        pathItem.get.parameters[0].in == ParameterIn.PATH.toString()
        pathItem.get.parameters[0].required
        pathItem.get.parameters[0].schema
        pathItem.get.parameters[0].description == 'The slug name'
        pathItem.get.parameters[0].schema.type == 'string'
        pathItem.get.responses.size() == 1
        pathItem.get.responses['default'] != null
        pathItem.get.responses['default'].content['application/json'].schema.type == 'string'

        when:"A flowable is returned"
        pathItem = openAPI.paths.get("/pets/flowable")

        then:
        pathItem.get.operationId == 'flowable'
        pathItem.get.responses['default']
        pathItem.get.responses['default'].description == 'a list of pet names'
        pathItem.get.responses['default'].content['application/json'].schema
        pathItem.get.responses['default'].content['application/json'].schema.type == 'array'
    }

    void "test parse custom parameter data"() {
        given:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;

@Controller("/")
class MyController {

    @Get("/subscription/{subscriptionId}")
    public String getSubscription(
               @Parameter(in = ParameterIn.PATH, name = "subscriptionId",
               \t\t\trequired = true, description = "parameter description",
               \t\t\tallowEmptyValue = true, allowReserved = true,
               \t\t\tschema = @Schema(
                                    type = "string",
                                    format = "uuid",
                                    description = "the generated UUID")) String subscriptionId) { 
        return null;                               
     }
}

@javax.inject.Singleton
class MyBean {}
''')
        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/subscription/{subscriptionId}")?.get

        expect:
        operation != null
        operation.operationId == 'getSubscription'
        operation.parameters.size() == 1
        operation.parameters[0].in == 'path'
        operation.parameters[0].name == 'subscriptionId'
        operation.parameters[0].description == 'parameter description'
        operation.parameters[0].required
        operation.parameters[0].allowEmptyValue
        operation.parameters[0].allowReserved
        operation.parameters[0].schema.type == 'string'
        operation.parameters[0].schema.format == 'uuid'
        operation.parameters[0].schema.description == 'the generated UUID'

    }

    void "test parse javax.validation constraints for String"() {
        given:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;
import javax.validation.constraints.*;

@Controller("/")
class MyController {

    @Get("/subscription/{subscriptionId}")
    public String getSubscription( @NotBlank @Max(10) @Min(5) @Pattern(regexp="xxxxx") @Size(min=10, max=20) String subscriptionId) { 
        return null;                               
     }
}

@javax.inject.Singleton
class MyBean {}
''')
        when:
        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/subscription/{subscriptionId}")?.get

        then:
        operation != null
        operation.operationId == 'getSubscription'
        operation.parameters.size() == 1


        when:
        def parameter = operation.parameters[0]

        then:
        parameter.in == 'path'
        parameter.schema.maxLength == 20
        parameter.schema.minLength == 10
        parameter.schema.format == 'xxxxx'
        parameter.schema.maximum == 10
        parameter.schema.minimum == 5

    }

    void "test parse javax.validation constraints for String[]"() {
        given:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;
import javax.validation.constraints.*;

@Controller("/")
class MyController {

    @Get("/subscription/{subscriptionId}")
    public String getSubscription( @Size(min=10, max=20) String[] subscriptionId) { 
        return null;                               
     }
}

@javax.inject.Singleton
class MyBean {}
''')
        when:
        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/subscription/{subscriptionId}")?.get

        then:
        operation != null
        operation.operationId == 'getSubscription'
        operation.parameters.size() == 1


        when:
        def parameter = operation.parameters[0]

        then:
        parameter.in == 'path'
        parameter.schema.maxLength == null
        parameter.schema.minLength == null
        parameter.schema.minItems == 10
        parameter.schema.maxItems == 20

    }

    void "test parse javax.validation constraints for List"() {
        given:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;
import javax.validation.constraints.*;

@Controller("/")
class MyController {

    @Get("/subscription/{subscriptionId}")
    public String getSubscription( @Size(min=10, max=20) java.util.List<String> subscriptionId) { 
        return null;                               
     }
}

@javax.inject.Singleton
class MyBean {}
''')
        when:
        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/subscription/{subscriptionId}")?.get

        then:
        operation != null
        operation.operationId == 'getSubscription'
        operation.parameters.size() == 1


        when:
        def parameter = operation.parameters[0]

        then:
        parameter.in == 'path'
        parameter.schema.maxLength == null
        parameter.schema.minLength == null
        parameter.schema.minItems == 10
        parameter.schema.maxItems == 20

    }

    void "test parse javax.validation.NotEmpty constraint for List"() {
        given:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;
import javax.validation.constraints.*;

@Controller("/")
class MyController {

    @Get("/subscription/{subscriptionId}")
    public String getSubscription( @NotEmpty java.util.List<String> subscriptionId) { 
        return null;                               
     }
}

@javax.inject.Singleton
class MyBean {}
''')
        when:
        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/subscription/{subscriptionId}")?.get

        then:
        operation != null
        operation.operationId == 'getSubscription'
        operation.parameters.size() == 1


        when:
        def parameter = operation.parameters[0]

        then:
        parameter.in == 'path'
        parameter.schema.minItems == 1

    }

    void "test parse @Header, @CookieValue, @QueryValue parameter data"() {
        given:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.enums.*;
import io.micronaut.http.annotation.*;
import java.util.List;

@Controller("/")
class MyController {

    @Get("/subscription/{subscriptionId}")
    public String getSubscription(@Parameter(description="foo") @CookieValue String subscriptionId, @QueryValue String q, @Header String contentType) { 
        return null;                               
     }
}

@javax.inject.Singleton
class MyBean {}
''')
        Operation operation = AbstractOpenApiVisitor.testReference?.paths?.get("/subscription/{subscriptionId}")?.get

        expect:
        operation != null
        operation.operationId == 'getSubscription'
        operation.parameters.size() == 3
        operation.parameters[0].in == 'cookie'
        operation.parameters[0].name == 'subscriptionId'
        operation.parameters[0].required
        operation.parameters[0].description == 'foo'
        operation.parameters[1].in == 'query'
        operation.parameters[1].name == 'q'
        operation.parameters[1].required
        operation.parameters[2].in == 'header'
        operation.parameters[2].name == 'content-type'
        operation.parameters[2].required


    }
}
