package io.micronaut.docs.server.json

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class PersonControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, ["spec.name": getClass().simpleName])

    @Shared @AutoCleanup HttpClient client = HttpClient.create(embeddedServer.URL)

    void "test global error handler"() {
        when:
        Flux.from(client.exchange("/people/error", Map.class)).blockFirst()
        
        then:
        HttpClientResponseException e = thrown()
        HttpResponse<Map> response = (HttpResponse<Map>) e.getResponse()
        response.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR
        response.getBody().get().get("message") == "Bad Things Happened: Something went wrong"
    }

    void testSave() {
        when:
        HttpResponse<Person> response =  Flux.from(client.exchange(HttpRequest.POST("/people", "{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":45}"), Person.class)).blockFirst()
        Person person = response.getBody().get()
        
        then:
        person.getFirstName() == "Fred"
        response.getStatus() == HttpStatus.CREATED
    }

    void testGetPerson() {
        when:
        HttpResponse<Person> response = client.exchange(HttpRequest.GET("/people/Fred"), Person.class).blockFirst()
        Person person = response.getBody().get()

        then:
        person.getFirstName() == "Fred"
        response.getStatus() == HttpStatus.OK
    }

    void testSaveReactive() {
        HttpResponse<Person> response = Flux.from(client.exchange(HttpRequest.POST("/people/saveReactive", "{\"firstName\":\"Wilma\",\"lastName\":\"Flintstone\",\"age\":36}"), Person.class)).blockFirst()
        Person person = response.getBody().get()

        expect:
        person.getFirstName() == "Wilma"
        response.getStatus() == HttpStatus.CREATED
    }

    void testSaveFuture() {
        HttpResponse<Person> response =  Flux.from(client.exchange(HttpRequest.POST("/people/saveFuture", "{\"firstName\":\"Pebbles\",\"lastName\":\"Flintstone\",\"age\":0}"), Person.class)).blockFirst()
        Person person = response.getBody().get()

        expect:
        person.getFirstName() == "Pebbles"
        response.getStatus() == HttpStatus.CREATED
    }

    void testSaveArgs() {
        HttpResponse<Person> response =  Flux.from(client.exchange(HttpRequest.POST("/people/saveWithArgs", "{\"firstName\":\"Dino\",\"lastName\":\"Flintstone\",\"age\":3}"), Person.class)).blockFirst()
        Person person = response.getBody().get()

        expect:
        person.getFirstName() == "Dino"
        response.getStatus() == HttpStatus.CREATED
    }

    void testPersonNotFound() {
        when:
        Flux.from(client.exchange("/people/Sally", Map.class)).blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        HttpResponse<Map> response = (HttpResponse<Map>) e.getResponse()
        response.getBody().get().get("message") == "Person Not Found"
        response.getStatus() == HttpStatus.NOT_FOUND
    }

    void testSaveInvalidJson() {
        when:
        Flux.from(client.exchange(HttpRequest.POST("/people", "{\""), Argument.of(Person.class), Argument.of(Map.class))).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<Map> response = (HttpResponse<Map>) e.getResponse()
        response.getBody(Map.class).get().get("message").toString().startsWith("Invalid JSON: Unexpected end-of-input")
        response.getStatus() == HttpStatus.BAD_REQUEST
    }
    
}
