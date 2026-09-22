package io.micronaut.validation.routes

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.web.router.AnnotatedMethodRouteBuilder
import io.micronaut.web.router.DefaultRouter
import io.micronaut.web.router.PrecompiledHttpRoutesDefinition
import io.micronaut.web.router.UriRouteInfo
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy

class PrecompiledHttpRoutesSpec extends AbstractTypeElementSpec {

    private static final String CONTROLLERS = '''
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import java.util.List;

@Controller("/items")
@Produces(MediaType.TEXT_PLAIN)
class ItemController {
    @Get("/{id}")
    String show(Long id) { return ""; }

    @Get(uri = "/no-head", headRoute = false)
    String noHead() { return ""; }

    @Get(uris = {"/one", "/two"})
    String many() { return ""; }

    @Get
    String index() { return ""; }

    @Post(consumes = MediaType.TEXT_PLAIN)
    String save(@Body String body) { return ""; }

    @Put("/{id}")
    String update(Long id, @Body String body) { return ""; }

    @Patch("/{id}")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    String patch(Long id, @Body String body) { return ""; }

    @Delete("/{id}")
    void delete(Long id) { }

    @Options
    void options() { }

    @Head("/head")
    void head() { }

    @Trace("/trace")
    void trace() { }

    @CustomHttpMethod(method = "PROPFIND", value = "/{id}")
    String propfind(Long id) { return ""; }

    @Get("/arrays{?names,counts}")
    String arrays(@jakarta.annotation.Nullable @QueryValue String[] names, @jakarta.annotation.Nullable @QueryValue int[] counts, HttpRequest<?> request) { return ""; }

    @Get("/list{?names}")
    String list(@jakarta.annotation.Nullable @QueryValue List<String> names, @jakarta.annotation.Nullable int[][] matrix) { return ""; }

    @io.micronaut.http.annotation.Error
    String error(IllegalStateException e) { return ""; }
}

abstract class BaseController {
    @Get("/inherited")
    String inherited() { return ""; }
}

@Controller("/sub")
class SubController extends BaseController {
    @Get("/own{/rest:.*}")
    String own(@jakarta.annotation.Nullable String rest) { return ""; }
}

interface Api {
    @Post("/api/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    String api(String name);
}

@Controller
class ApiController implements Api {
    @Override
    public String api(String name) { return ""; }
}

@Controller(value = "/port", port = "8123")
class PortController {
    @Get
    String port() { return ""; }
}

@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@UriMapping
@io.micronaut.context.annotation.Executable
@interface Plain {
    @io.micronaut.context.annotation.AliasFor(annotation = UriMapping.class, member = "value")
    String value() default "/";
}

@Controller("/mapping")
class MappingController {
    @Plain("/plain")
    String plain() { return ""; }
}

@Controller("${test.placeholder:/placeholder}")
class PlaceholderController {
    @Get("/x")
    String x() { return ""; }
}
'''

    void "precompiled routes match the runtime routes"() {
        given:
        ApplicationContext runtimeContext = buildContext('test.ItemController', 'package test;' + CONTROLLERS)
        ApplicationContext precompiledContext = buildContext('test.ItemController', 'package test;' + CONTROLLERS + '''
@io.micronaut.web.router.annotation.PrecompiledHttpRoutes
class Application {
}
''')
        def definition = precompiledContext.classLoader.loadClass('test.$Application$PrecompiledHttpRoutes')
            .getDeclaredConstructor().newInstance() as PrecompiledHttpRoutesDefinition
        // the in-memory class loader cannot list the service entries
        def runtimeBuilder = routeBuilder(runtimeContext, [])
        def precompiledBuilder = routeBuilder(precompiledContext, [definition])
        def runtimeRouter = new DefaultRouter(runtimeBuilder)
        def precompiledRouter = new DefaultRouter(precompiledBuilder)

        expect: 'every controller but the one with a placeholder is precompiled'
        definition.controllerTypes() as Set == [
            'test.ItemController', 'test.SubController', 'test.ApiController', 'test.PortController', 'test.MappingController'
        ] as Set
        runtimeBuilder.uriRoutes.size() == 30
        precompiledBuilder.uriRoutes*.toString() == ['GET /placeholder/x -> PlaceholderController#x (application/json)', 'HEAD /placeholder/x -> PlaceholderController#x (application/json)']

        and: 'the routers have the same routes'
        routes(precompiledRouter) == routes(runtimeRouter)
        routes(precompiledRouter).size() == 30

        and: 'the precompiled routes are built when used'
        precompiledRouter.uriRoutes()
            .filter { it.declaringType.name != 'test.PlaceholderController' }
            .allMatch { it.class.simpleName == 'LazyUriRouteInfo' }

        and: 'error routes and exposed ports are registered'
        precompiledBuilder.errorRoutes.size() == runtimeBuilder.errorRoutes.size()
        precompiledRouter.exposedPorts == runtimeRouter.exposedPorts
        precompiledRouter.exposedPorts == [8123] as Set

        and: 'the routers match the same routes'
        for (def request : [
            HttpRequest.GET('/items/10'),
            HttpRequest.HEAD('/items/10'),
            HttpRequest.GET('/items/no-head'),
            HttpRequest.HEAD('/items/no-head'),
            HttpRequest.GET('/items/two'),
            HttpRequest.GET('/items'),
            HttpRequest.POST('/items', 'x').contentType('text/plain'),
            HttpRequest.DELETE('/items/10'),
            HttpRequest.OPTIONS('/items'),
            HttpRequest.create(HttpMethod.CUSTOM, '/items/10', 'PROPFIND'),
            HttpRequest.GET('/items/arrays?names=a'),
            HttpRequest.GET('/sub/inherited'),
            HttpRequest.GET('/sub/own/a/b'),
            HttpRequest.POST('/api/foo', 'x'),
            HttpRequest.GET('/mapping/plain'),
            HttpRequest.GET('/placeholder/x'),
            HttpRequest.GET('/missing'),
        ]) {
            assert target(precompiledRouter, request) == target(runtimeRouter, request)
        }
        target(runtimeRouter, HttpRequest.GET('/sub/inherited')) == 'test.SubController#inherited'
        // a UriMapping stereotype is not a route
        target(runtimeRouter, HttpRequest.GET('/mapping/plain')) == null

        cleanup:
        runtimeContext?.close()
        precompiledContext?.close()
    }

    void "two annotated classes fail the compilation"() {
        when:
        buildClassLoader('test.Application', '''
package test;

@io.micronaut.web.router.annotation.PrecompiledHttpRoutes
class Application {
}

@io.micronaut.web.router.annotation.PrecompiledHttpRoutes
class Other {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Only one class can be annotated with @PrecompiledHttpRoutes')
    }

    private static AnnotatedMethodRouteBuilder routeBuilder(ApplicationContext context, List<PrecompiledHttpRoutesDefinition> definitions) {
        def builder = new AnnotatedMethodRouteBuilder(context, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, definitions)
        for (def definition : context.getBeanDefinitions(Qualifiers.byStereotype(Controller))) {
            builder.process(definition, context)
        }
        return builder
    }

    private static List<String> routes(DefaultRouter router) {
        return router.uriRoutes()
            .map { UriRouteInfo<?, ?> route ->
                [
                    route.httpMethodName,
                    route.uriMatchTemplate.toString(),
                    route.declaringType.name + '#' + route.targetMethod.methodName,
                    route.targetMethod.argumentTypes*.name.join(','),
                    route.consumes*.toString().join(','),
                    route.produces*.toString().join(','),
                    route.implicitHead,
                    route.port
                ].join(' ')
            }
            .distinct()
            .sorted()
            .toList()
    }

    private static String target(DefaultRouter router, HttpRequest<?> request) {
        def match = router.findClosest(request)
        return match == null ? null : match.routeInfo.declaringType.name + '#' + match.routeInfo.targetMethod.methodName
    }
}
