package io.micronaut.web.router.proof

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.HttpRoutes
import io.micronaut.web.router.Router

/**
 * Proof that handler routes are a compilation target for other web annotation models: the
 * {@link CustomWebRoutesVisitor} reads the annotations of a made-up framework at compile time and
 * writes an {@link HttpRoutes} bean, whose handler functions get the resource bean from the bean
 * context and call its methods directly. The routes then run on the Netty server like controller
 * routes, with no {@code @Controller}, no executable methods and no reflection.
 */
class CustomWebAnnotationsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package petstore.web;

import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @interface Resource { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Read { String value() default ""; }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Write { String value() default ""; }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface Param { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface Field { String value(); }

@Singleton
@Resource("/pets")
class PetResource {
    private final Map<Long, String> pets = new ConcurrentHashMap<>(Map.of(1L, "Rex"));
    private final AtomicLong ids = new AtomicLong(1);

    @Read("/{id}")
    String name(@Param("id") long id) {
        return pets.getOrDefault(id, "unknown");
    }

    @Read("/{id}/owners/{owner}")
    String owned(@Param("id") long id, @Param("owner") UUID owner) {
        return pets.get(id) + " of " + owner;
    }

    @Write
    String add(@Field("name") String name, @Field("age") int age) {
        long id = ids.incrementAndGet();
        pets.put(id, name + " (" + age + ")");
        return String.valueOf(id);
    }

    @Write("/{id}/rename")
    void rename(@Param("id") long id, @Field("name") String name) {
        pets.put(id, name);
    }
}
'''

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new CustomWebRoutesVisitor()]
    }

    void "routes written at compile time from custom annotations call the bean's methods"() {
        given:
        ApplicationContext context = buildContext('petstore.web.PetResource', SOURCE, true, ['micronaut.server.port': -1])
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, server.URL)
        def http = client.toBlocking()

        expect: 'the annotation processor wrote an HttpRoutes bean, and there is no controller'
        context.getBeanDefinitions(HttpRoutes)*.beanType*.name == ['petstore.web.PetResourceRoutes']
        context.getBeanDefinitions(Qualifiers.byStereotype(Controller)).isEmpty()

        and: 'the resource is a plain bean: its methods are called by the generated code, not through executable methods'
        context.getBeanDefinition(context.classLoader.loadClass('petstore.web.PetResource')).executableMethods.isEmpty()

        and: 'the routes are handler routes of the router'
        context.getBean(Router).uriRoutes()
            .filter { it.uriMatchTemplate.toString().startsWith('/pets') }
            .map { it.httpMethodName + ' ' + it.uriMatchTemplate }
            .distinct()
            .sorted()
            .toList() == ['GET /pets/{id}', 'GET /pets/{id}/owners/{owner}', 'HEAD /pets/{id}', 'HEAD /pets/{id}/owners/{owner}',
                          'POST /pets', 'POST /pets/{id}/rename']

        and: 'path variables are converted to the parameter types'
        http.retrieve('/pets/1') == 'Rex'
        http.retrieve('/pets/1/owners/3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e') == 'Rex of 3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e'

        when: 'a form is posted'
        String id = http.retrieve(HttpRequest.POST('/pets', [name: 'Bella', age: '3']).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

        then: 'the fields are converted to the parameter types'
        http.retrieve("/pets/$id") == 'Bella (3)'

        when: 'a void method is called'
        def renamed = http.exchange(HttpRequest.POST("/pets/$id/rename", [name: 'Luna']).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

        then:
        renamed.status == HttpStatus.NO_CONTENT
        http.retrieve("/pets/$id") == 'Luna'

        when: 'a path variable does not convert'
        http.retrieve('/pets/rex')

        then: 'the request is answered with 400, like a controller'
        def badRequest = thrown(HttpClientResponseException)
        badRequest.status == HttpStatus.BAD_REQUEST

        when: 'a form field is missing'
        http.retrieve(HttpRequest.POST('/pets', [name: 'Max']).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

        then:
        def missing = thrown(HttpClientResponseException)
        missing.status == HttpStatus.BAD_REQUEST

        when: 'the method is not routed'
        http.exchange(HttpRequest.DELETE('/pets/1'))

        then:
        def notAllowed = thrown(HttpClientResponseException)
        notAllowed.status == HttpStatus.METHOD_NOT_ALLOWED

        cleanup:
        client?.close()
        server?.stop()
        context?.close()
    }
}
