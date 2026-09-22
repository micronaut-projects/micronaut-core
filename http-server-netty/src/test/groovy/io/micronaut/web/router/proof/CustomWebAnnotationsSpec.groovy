package io.micronaut.web.router.proof

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.CompiledRouteMatcher
import io.micronaut.web.router.RouteDeclaration
import io.micronaut.web.router.Router

/**
 * Proof that routes can be declared at compile time from other web annotation models and
 * implemented by handler functions: the {@link CustomWebRoutesVisitor} reads the annotations of a
 * made-up framework and generates an enum of {@link RouteDeclaration}s with precomputed index
 * keys. A functional router binds a handler to each constant, calling the resource bean from the
 * bean context. The router registers the routes lazily, without parsing their templates, and they
 * run on the Netty server like controller routes.
 */
class CustomWebAnnotationsSpec extends AbstractTypeElementSpec {

    /**
     * The constants of the generated enum, loaded from the in-memory compilation.
     */
    static class RouteDeclarationsHolder {
        static Object[] constants
    }

    private static final String SOURCE = '''
package petstore.web;

import io.micronaut.context.BeanProvider;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.HttpRoutes;
import io.micronaut.web.router.RouteBuilder;
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

@Singleton
@Resource("/pets")
class PetResource {
    private final Map<Long, String> pets = new ConcurrentHashMap<>(Map.of(1L, "Rex"));
    private final AtomicLong ids = new AtomicLong(1);

    @Read("/{id}")
    String name(long id) {
        return pets.getOrDefault(id, "unknown");
    }

    @Read("/{id}/owners/{owner}")
    String owned(long id, UUID owner) {
        return pets.get(id) + " of " + owner;
    }

    @Read("/{id}/photo")
    byte[] photo(long id) {
        return new byte[0];
    }

    @Read("/{id}/files/{+file}")
    String file(long id, String file) {
        return pets.get(id) + ": " + file;
    }

    @Write
    String add(String name, int age) {
        long id = ids.incrementAndGet();
        pets.put(id, name + " (" + age + ")");
        return String.valueOf(id);
    }

    @Write("/{id}/rename")
    void rename(long id, String name) {
        pets.put(id, name);
    }
}

/**
 * The functional router: binds a handler function to each declared route it implements, and
 * calls the resource bean.
 */
@Singleton
class PetRoutes implements HttpRoutes {
    private final BeanProvider<PetResource> pets;

    PetRoutes(BeanProvider<PetResource> pets) {
        this.pets = pets;
    }

    @Override
    public void routes(RouteBuilder routes) {
        routes.handle(PetResourceRoutes.NAME, (request, path) ->
            HttpResponse.ok(pets.get().name(path.getLong("id"))));
        routes.handle(PetResourceRoutes.OWNED, (request, path) ->
            HttpResponse.ok(pets.get().owned(path.getLong("id"), path.get("owner", UUID.class))));
        routes.handleForm(PetResourceRoutes.ADD, (request, path, form) ->
            HttpResponse.ok(pets.get().add(form.getString("name"), form.getInt("age"))));
        routes.handleForm(PetResourceRoutes.RENAME, (request, path, form) -> {
            pets.get().rename(path.getLong("id"), form.getString("name"));
            return HttpResponse.noContent();
        });
        routes.handle(PetResourceRoutes.FILE, (request, path) ->
            HttpResponse.ok(pets.get().file(path.getLong("id"), path.getString("file"))));
        // PetResourceRoutes.PHOTO is declared but not bound: it is not a route
    }
}
'''

    private static List<String> parse(CompiledRouteMatcher matcher, HttpMethod method, String path) {
        String[] variables = new String[matcher.maxVariables()]
        int ordinal = matcher.match(method, path, variables)
        if (ordinal < 0) {
            return []
        }
        def declaration = RouteDeclarationsHolder.constants[ordinal] as RouteDeclaration
        return [((Enum) declaration).name()] + variables.toList().subList(0, declaration.pathVariableCount())
    }

    private static String matchedBy(Router router, HttpRequest<?> request) {
        def match = router.findClosest(request)
        return match.@matchInfo.class.simpleName
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new CustomWebRoutesVisitor()]
    }

    void "routes declared at compile time from custom annotations are implemented by handler functions"() {
        given:
        ApplicationContext context = buildContext('petstore.web.PetResource', SOURCE, true, ['micronaut.server.port': -1])
        Class<?> declarations = context.classLoader.loadClass('petstore.web.PetResourceRoutes')
        RouteDeclarationsHolder.constants = declarations.enumConstants

        expect: 'the annotation processor generated an enum of route declarations'
        declarations.isEnum()
        RouteDeclaration.isAssignableFrom(declarations)
        declarations.enumConstants*.name() == ['NAME', 'OWNED', 'PHOTO', 'FILE', 'ADD', 'RENAME']
        declarations.enumConstants.collect { RouteDeclaration d -> d.httpMethod().name() + ' ' + d.uriTemplate() } ==
                ['GET /pets/{id}', 'GET /pets/{id}/owners/{owner}', 'GET /pets/{id}/photo', 'GET /pets/{id}/files/{+file}', 'POST /pets', 'POST /pets/{id}/rename']

        and: 'its index keys are the ones the router computes for a route built at runtime'
        declarations.enumConstants.every { RouteDeclaration d ->
            def runtime = RouteDeclaration.of(d.httpMethod(), d.uriTemplate())
            d.requiredPathPrefix() == runtime.requiredPathPrefix() && d.rawLength() == runtime.rawLength() && d.pathVariableCount() == runtime.pathVariableCount()
        }

        and: 'the generated URL parser maps a request path to a constant and captures the path variables'
        CompiledRouteMatcher matcher = (declarations.enumConstants[0] as RouteDeclaration).matcher()
        matcher.maxVariables() == 2
        parse(matcher, HttpMethod.GET, '/pets/7') == ['NAME', '7']
        parse(matcher, HttpMethod.GET, '/pets/7/owners/abc') == ['OWNED', '7', 'abc']
        parse(matcher, HttpMethod.GET, '/pets/7/photo') == ['PHOTO', '7']
        parse(matcher, HttpMethod.POST, '/pets') == ['ADD']
        parse(matcher, HttpMethod.POST, '/pets/7/rename') == ['RENAME', '7']

        and: 'it answers nothing for another method, an unknown path, or a template it leaves to the router'
        parse(matcher, HttpMethod.GET, '/pets') == []
        parse(matcher, HttpMethod.DELETE, '/pets/7') == []
        parse(matcher, HttpMethod.GET, '/pets/7/unknown') == []
        parse(matcher, HttpMethod.GET, '/cats/7') == []
        parse(matcher, HttpMethod.GET, '/pets/7/files/a/b') == []

        and: 'there is no controller, and the resource is a plain bean without executable methods'
        context.getBeanDefinitions(Qualifiers.byStereotype(Controller)).isEmpty()
        context.getBeanDefinition(context.classLoader.loadClass('petstore.web.PetResource')).executableMethods.isEmpty()

        when:
        Router router = context.getBean(Router)
        def petRoutes = router.uriRoutes().filter { it.toString().contains('/pets') }.toList()

        then: 'the bound declarations are routes, with implicit HEAD, registered lazily with their precomputed keys'
        petRoutes.collect { it.httpMethodName + ' ' + it.toString().split(' ')[1] }.toSet() ==
                ['GET /pets/{id}', 'HEAD /pets/{id}', 'GET /pets/{id}/owners/{owner}', 'HEAD /pets/{id}/owners/{owner}',
                 'GET /pets/{id}/files/{+file}', 'HEAD /pets/{id}/files/{+file}', 'POST /pets', 'POST /pets/{id}/rename'] as Set
        petRoutes.every { it.class.simpleName == 'LazyUriRouteInfo' }

        and: 'the router resolves a request with the generated parser, the route selected by the ordinal'
        matchedBy(router, HttpRequest.GET('/pets/1')) == 'CapturedUriMatchInfo'
        matchedBy(router, HttpRequest.HEAD('/pets/1')) == 'CapturedUriMatchInfo'
        matchedBy(router, HttpRequest.POST('/pets', [name: 'x', age: '1']).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) == 'CapturedUriMatchInfo'

        and: 'a template the parser does not compile is matched by the router'
        matchedBy(router, HttpRequest.GET('/pets/1/files/a/b')) != 'CapturedUriMatchInfo'

        when:
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, server.URL)
        def http = client.toBlocking()

        then: 'the handlers call the resource with typed path variables'
        http.retrieve('/pets/1') == 'Rex'
        http.retrieve('/pets/1/owners/3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e') == 'Rex of 3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e'
        http.exchange(HttpRequest.HEAD('/pets/1')).status == HttpStatus.OK
        http.retrieve('/pets/1/files/a/b') == 'Rex: a/b'

        when: 'a form is posted'
        String id = http.retrieve(HttpRequest.POST('/pets', [name: 'Bella', age: '3']).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

        then:
        http.retrieve("/pets/$id") == 'Bella (3)'

        when: 'a void method is called'
        def renamed = http.exchange(HttpRequest.POST("/pets/$id/rename", [name: 'Luna']).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

        then:
        renamed.status == HttpStatus.NO_CONTENT
        http.retrieve("/pets/$id") == 'Luna'

        when: 'a declared route has no handler'
        http.retrieve('/pets/1/photo')

        then: 'it is not a route'
        def notFound = thrown(HttpClientResponseException)
        notFound.status == HttpStatus.NOT_FOUND

        when: 'a path variable does not convert'
        http.retrieve('/pets/rex')

        then:
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
