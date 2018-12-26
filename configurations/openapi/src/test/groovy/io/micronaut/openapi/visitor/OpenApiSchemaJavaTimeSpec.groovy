package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation

class OpenApiSchemaJavaTimeSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    void "test parse the OpenAPI with response that contains Java 8 date-time types"() {
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
import java.time.*;
import java.util.List;

@Controller("/")
class MyController {

    @Put("/")
    public Response<Pet> updatePet(Pet pet) {
        return null;
    }
}

class Pet {
    private LocalDateTime local;

    public LocalDateTime getLocal() { return local; }
    public void setLocal(LocalDateTime newValue) { this.local = newValue; }

    private OffsetDateTime offset;

    public OffsetDateTime getOffset() { return offset; }
    public void setOffsetDateTime(OffsetDateTime newValue) { this.offset = newValue; }

    private ZonedDateTime zoned;

    public ZonedDateTime getZoned() { return zoned; }
    public void setZoned(ZonedDateTime newValue) { this.zoned = newValue; }

    private Instant instant;

    public Instant getInstant() { return instant; }
    public void setInstant(Instant newValue) { this.instant = newValue; }

}

class Response<T> {
    T r;
    public T getResult() {
        return r;
    };
}

@javax.inject.Singleton
class MyBean {}
''')

        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Operation operation = openAPI.paths?.get("/")?.put

        expect:
        operation
        operation.responses.size() == 1
        openAPI.components.schemas['Pet'].properties['local'].type == 'string'
        openAPI.components.schemas['Pet'].properties['local'].format == 'date-time'
        openAPI.components.schemas['Pet'].properties['offset'].type == 'string'
        openAPI.components.schemas['Pet'].properties['offset'].format == 'date-time'
        openAPI.components.schemas['Pet'].properties['zoned'].type == 'string'
        openAPI.components.schemas['Pet'].properties['zoned'].format == 'date-time'
        openAPI.components.schemas['Pet'].properties['instant'].type == 'integer'
        openAPI.components.schemas['Pet'].properties['instant'].format == 'int64'
        openAPI.components.schemas['Response<Pet>'].properties['result'].$ref == '#/components/schemas/Pet'
    }

    void "test parse the OpenAPI with response that contains Java 8 date types"() {
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
import java.time.*;
import java.util.List;

@Controller("/")
class MyController {

    @Put("/")
    public Response<Pet> updatePet(Pet pet) {
        return null;
    }
}

class Pet {
    private LocalDate local;

    public LocalDate getLocal() { return local; }
    public void setLocal(LocalDate newValue) { this.local = newValue; }

}

class Response<T> {
    T r;
    public T getResult() {
        return r;
    };
}

@javax.inject.Singleton
class MyBean {}
''')

        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        Operation operation = openAPI.paths?.get("/")?.put

        expect:
        operation
        operation.responses.size() == 1
        openAPI.components.schemas['Pet'].properties['local'].type == 'string'
        openAPI.components.schemas['Pet'].properties['local'].format == 'date'
        openAPI.components.schemas['Response<Pet>'].properties['result'].$ref == '#/components/schemas/Pet'
    }

}
