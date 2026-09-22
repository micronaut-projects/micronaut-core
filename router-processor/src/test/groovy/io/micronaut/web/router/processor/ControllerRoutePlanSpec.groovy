package io.micronaut.web.router.processor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.uri.UriTemplateMatcher
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.web.router.AnnotatedMethodRouteBuilder
import io.micronaut.web.router.DefaultRouter
import io.micronaut.web.router.UriRouteInfo
import io.micronaut.web.router.UriRouteMatch
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy
import io.micronaut.web.router.spi.RouteCandidateSink
import io.micronaut.web.router.spi.RoutePlan
import io.micronaut.web.router.spi.RouteSlot

/**
 * The route plans of controllers: the routes a plan describes are the routes the runtime derives,
 * and a router that uses the plans selects the same routes as one that derives them.
 */
class ControllerRoutePlanSpec extends AbstractTypeElementSpec {

    private static final List<String> PLANNED = ['ItemController', 'SubController', 'ApiController', 'PortController', 'MappingController']

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

    void "the routes of controller plans match the runtime routes"() {
        given:
        ApplicationContext context = buildContext('test.ItemController', 'package test;' + CONTROLLERS)
        List<RoutePlan> plans = plans(context)
        def runtimeBuilder = routeBuilder(context, [])
        def compiledBuilder = routeBuilder(context, plans)
        def runtimeRouter = new DefaultRouter(runtimeBuilder)
        def compiledRouter = new DefaultRouter(compiledBuilder)

        expect: 'every controller but the one with a placeholder has a plan'
        plans*.owners().flatten() as Set == PLANNED.collect { 'test.' + it } as Set
        plans*.id() as Set == PLANNED.collect { 'controller:test.' + it } as Set
        !exists(context, 'test.$PlaceholderController$RoutePlan')
        runtimeBuilder.uriRoutes.size() == 30
        compiledBuilder.uriRoutes*.toString() == ['GET /placeholder/x -> PlaceholderController#x (application/json)', 'HEAD /placeholder/x -> PlaceholderController#x (application/json)']

        and: 'the routers have the same routes'
        routes(compiledRouter) == routes(runtimeRouter)
        routes(compiledRouter).size() == 30

        and: 'the routes of the plans are built when used'
        compiledRouter.uriRoutes()
            .filter { it.declaringType.name != 'test.PlaceholderController' }
            .allMatch { it.class.simpleName == 'LazyUriRouteInfo' }

        and: 'error routes and exposed ports are registered'
        compiledBuilder.errorRoutes.size() == runtimeBuilder.errorRoutes.size()
        compiledRouter.exposedPorts == runtimeRouter.exposedPorts
        compiledRouter.exposedPorts == [8123] as Set

        and: 'the routers match the same routes'
        for (def request : REQUESTS) {
            assert target(compiledRouter, request) == target(runtimeRouter, request)
            assert variables(compiledRouter, request) == variables(runtimeRouter, request)
            assert compiledRouter.findAllClosest(request).collect { describe(it) } == runtimeRouter.findAllClosest(request).collect { describe(it) }
            assert compiledRouter.findAny(request).collect { describe(it) } as Set == runtimeRouter.findAny(request).collect { describe(it) } as Set
            assert compiledRouter.find(request).map { describe(it) }.toList() == runtimeRouter.find(request).map { describe(it) }.toList()
        }
        target(runtimeRouter, HttpRequest.GET('/sub/inherited')) == 'test.SubController#inherited'
        // a UriMapping stereotype is not a route
        target(runtimeRouter, HttpRequest.GET('/mapping/plain')) == null

        and: 'the parsers of the plans matched the compiled routes'
        matchedBy(compiledRouter, HttpRequest.GET('/items/10')) == 'CapturedUriMatchInfo'
        matchedBy(compiledRouter, HttpRequest.HEAD('/items/10')) == 'CapturedUriMatchInfo'
        matchedBy(compiledRouter, HttpRequest.create(HttpMethod.CUSTOM, '/items/10', 'PROPFIND')) == 'CapturedUriMatchInfo'
        matchedBy(compiledRouter, HttpRequest.POST('/api/foo', 'x')) == 'CapturedUriMatchInfo'
        // a template the parser does not compile is matched by its engine
        matchedBy(compiledRouter, HttpRequest.GET('/sub/own/a/b')) != 'CapturedUriMatchInfo'
        matchedBy(runtimeRouter, HttpRequest.GET('/items/10')) != 'CapturedUriMatchInfo'

        cleanup:
        context?.close()
    }

    void "a plan describes the routes of its controller as slots"() {
        given:
        ApplicationContext context = buildContext('test.ItemController', 'package test;' + CONTROLLERS)
        RoutePlan plan = plan(context, 'test.$ItemController$RoutePlan')
        RouteSlot[] slots = plan.slots()
        Map<String, RouteSlot> byKey = slots.collectEntries { [(it.key()): it] }

        expect: 'the keys are the logical identities of the declarations'
        byKey.keySet().containsAll([
            'controller:test.ItemController#show(java.lang.Long)@GET[0]',
            'controller:test.ItemController#show(java.lang.Long)@HEAD~implicit[0]',
            'controller:test.ItemController#many()@GET[0]',
            'controller:test.ItemController#many()@GET[1]',
            'controller:test.ItemController#propfind(java.lang.Long)@PROPFIND[0]',
            'controller:test.ItemController#arrays([Ljava.lang.String;,[I,io.micronaut.http.HttpRequest)@GET[0]',
            'controller:test.ItemController#list(java.util.List,[[I)@GET[0]',
        ])
        slots.length == byKey.size()
        plan.id() == 'controller:test.ItemController'
        plan.abiVersion() == RoutePlan.ABI_VERSION
        plan.fingerprint() == RoutePlan.fingerprint(slots)
        plan.commonPrefix() == '/items'
        plan.maxCaptures() == 1

        and: 'the slots carry the facts of their templates and the reasons they are matched at runtime'
        with(byKey['controller:test.ItemController#show(java.lang.Long)@GET[0]']) {
            template().expression() == '/items/{id}'
            compiled()
            captures() == ['id'] as String[]
            requiredPrefix() == new UriTemplateMatcher('/items/{id}').requiredPrefix
            controller().produces() == ['text/plain'] as String[]
            !controller().implicitHead()
        }
        byKey['controller:test.ItemController#show(java.lang.Long)@HEAD~implicit[0]'].controller().implicitHead()
        byKey['controller:test.ItemController#propfind(java.lang.Long)@PROPFIND[0]'].httpMethod() == HttpMethod.CUSTOM
        // query variables do not take part in matching the path
        byKey['controller:test.ItemController#arrays([Ljava.lang.String;,[I,io.micronaut.http.HttpRequest)@GET[0]'].compiled()

        and: 'the parser enumerates every compiled slot of a path'
        parse(plan, '/items/10') as Set == ['show:GET', 'show:HEAD', 'update:PUT', 'patch:PATCH', 'delete:DELETE', 'propfind:PROPFIND'] as Set
        parse(plan, '/items') as Set == ['index:GET', 'index:HEAD', 'save:POST', 'options:OPTIONS'] as Set
        parse(plan, '/items/no-head') as Set == ['noHead:GET', 'show:GET', 'show:HEAD', 'update:PUT', 'patch:PATCH', 'delete:DELETE', 'propfind:PROPFIND'] as Set
        parse(plan, '/items/a+b').isEmpty()
        parse(plan, '/other').isEmpty()

        cleanup:
        context?.close()
    }

    void "a template the parser does not compile is described with the reason"() {
        given:
        ApplicationContext context = buildContext('test.ItemController', 'package test;' + CONTROLLERS)
        RouteSlot own = plan(context, 'test.$SubController$RoutePlan').slots().find { it.controller().methodName() == 'own' }

        expect:
        !own.compiled()
        own.fallbackReason() == 'the template is not made of literal segments and whole-segment variables'
        own.captures().length == 0

        cleanup:
        context?.close()
    }

    void "the descriptor of a plan describes its slots and their lowering"() {
        given:
        ClassLoader classLoader = buildClassLoader('test.PetController', '''
package test;

import io.micronaut.http.annotation.*;

@Controller("/pets")
class PetController {
    @Get("/{id}")
    String show(long id) { return ""; }

    @Post(uri = "/{+path}", consumes = "text/plain")
    String files(String path, @Body String body) { return ""; }
}
''')
        String json = classLoader.getResources('META-INF/micronaut/routes/v1/controller_test.PetController.json').toList().last().text
        RoutePlan plan = classLoader.loadClass('test.$PetController$RoutePlan').getDeclaredConstructor().newInstance() as RoutePlan
        RouteDescriptors.Descriptor descriptor = RouteDescriptors.read(json)

        expect:
        json == """{
  "format": "micronaut-route-plan/1",
  "abiVersion": 1,
  "inputProfile": "micronaut-raw-path/1",
  "selectionPolicy": "micronaut/1",
  "id": "controller:test.PetController",
  "class": "test.\$PetController\$RoutePlan",
  "fingerprint": "${plan.fingerprint()}",
  "owners": ["test.PetController"],
  "slots": [
    {"key": "controller:test.PetController#show(long)@GET[0]", "method": "GET", "engine": "micronaut", "template": "/pets/{id}", "engineVersion": "1.0", "requiredPrefix": "/pets/", "rawLength": 6, "pathVariableCount": 1, "captures": ["id"], "compiled": true, "segments": [{"literal": "pets"}, {"variable": "io.micronaut.web.router.spi.RoutePlanSupport#micronautVariable"}], "controller": {"owner": "test.PetController", "method": "show", "argumentTypes": ["long"], "declaringTypeTarget": false, "consumes": null, "produces": ["application/json"], "implicitHead": false, "port": -1}},
    {"key": "controller:test.PetController#show(long)@HEAD~implicit[0]", "method": "HEAD", "engine": "micronaut", "template": "/pets/{id}", "engineVersion": "1.0", "requiredPrefix": "/pets/", "rawLength": 6, "pathVariableCount": 1, "captures": ["id"], "compiled": true, "segments": [{"literal": "pets"}, {"variable": "io.micronaut.web.router.spi.RoutePlanSupport#micronautVariable"}], "controller": {"owner": "test.PetController", "method": "show", "argumentTypes": ["long"], "declaringTypeTarget": false, "consumes": null, "produces": ["application/json"], "implicitHead": true, "port": -1}},
    {"key": "controller:test.PetController#files(java.lang.String,java.lang.String)@POST[0]", "method": "POST", "engine": "micronaut", "template": "/pets/{+path}", "engineVersion": "1.0", "requiredPrefix": "/pets/", "rawLength": 6, "pathVariableCount": 1, "captures": [], "compiled": false, "fallbackReason": "the template is not made of literal segments and whole-segment variables", "controller": {"owner": "test.PetController", "method": "files", "argumentTypes": ["java.lang.String", "java.lang.String"], "declaringTypeTarget": false, "consumes": ["text/plain"], "produces": ["application/json"], "implicitHead": false, "port": -1}}
  ]
}
"""

        and: 'the descriptor is read back without loading classes'
        descriptor.id() == plan.id()
        descriptor.className() == 'test.$PetController$RoutePlan'
        descriptor.fingerprint() == plan.fingerprint()
        descriptor.slots() == plan.slots().toList()
    }

    void "a plan that does not agree with the runtime is not used"() {
        given:
        ApplicationContext context = buildContext('test.ItemController', 'package test;' + CONTROLLERS)
        List<RoutePlan> plans = plans(context)
        // a parser generated for other slots
        List<RoutePlan> stale = plans.collect { RoutePlan plan -> new DelegatingPlan(plan, 'stale', plan.abiVersion()) }
        // a plan of another version of the contract
        List<RoutePlan> future = plans.collect { RoutePlan plan -> new DelegatingPlan(plan, plan.fingerprint(), RoutePlan.ABI_VERSION + 1) }
        def runtimeRouter = new DefaultRouter(routeBuilder(context, []))

        expect:
        for (List<RoutePlan> unusable : [stale, future]) {
            def builder = routeBuilder(context, unusable)
            def router = new DefaultRouter(builder)
            // the routes are derived at runtime, as without plans
            assert builder.uriRoutes.size() == 30
            assert routes(router) == routes(runtimeRouter)
            assert matchedBy(router, HttpRequest.GET('/items/10')) != 'CapturedUriMatchInfo'
        }

        cleanup:
        context?.close()
    }

    private static final List<HttpRequest<?>> REQUESTS = [
        HttpRequest.GET('/items/10'),
        HttpRequest.HEAD('/items/10'),
        HttpRequest.GET('/items/10/'),
        HttpRequest.GET('/items/no-head'),
        HttpRequest.HEAD('/items/no-head'),
        HttpRequest.GET('/items/two'),
        HttpRequest.GET('/items'),
        HttpRequest.GET('/items/'),
        HttpRequest.POST('/items', 'x').contentType('text/plain'),
        HttpRequest.POST('/items', 'x').contentType('application/json'),
        HttpRequest.PUT('/items/10', 'x'),
        HttpRequest.PATCH('/items/10', 'x').contentType('text/plain'),
        HttpRequest.DELETE('/items/10'),
        HttpRequest.DELETE('/items/a+b'),
        HttpRequest.OPTIONS('/items'),
        HttpRequest.create(HttpMethod.CUSTOM, '/items/10', 'PROPFIND'),
        HttpRequest.GET('/items/arrays?names=a'),
        HttpRequest.GET('/items/list'),
        HttpRequest.GET('/sub/inherited'),
        HttpRequest.GET('/sub/own/a/b'),
        HttpRequest.GET('/sub/own'),
        HttpRequest.POST('/api/foo', 'x'),
        HttpRequest.POST('/api/a%20b', 'x'),
        HttpRequest.GET('/port'),
        HttpRequest.GET('/mapping/plain'),
        HttpRequest.GET('/placeholder/x'),
        HttpRequest.GET('/missing'),
        HttpRequest.GET('/'),
    ]

    private List<RoutePlan> plans(ApplicationContext context) {
        // the in-memory class loader cannot list the service entries
        return PLANNED.collect { plan(context, 'test.$' + it + '$RoutePlan') }
    }

    private static RoutePlan plan(ApplicationContext context, String className) {
        return context.classLoader.loadClass(className).getDeclaredConstructor().newInstance() as RoutePlan
    }

    private static boolean exists(ApplicationContext context, String className) {
        try {
            context.classLoader.loadClass(className)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
        }
    }

    private static AnnotatedMethodRouteBuilder routeBuilder(ApplicationContext context, List<RoutePlan> plans) {
        def builder = new AnnotatedMethodRouteBuilder(context, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, plans)
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

    private static Map<String, Object> variables(DefaultRouter router, HttpRequest<?> request) {
        UriRouteMatch<?, ?> match = router.findClosest(request)
        return match == null ? null : [uri: match.uri, values: match.variableValues, names: match.variables*.name]
    }

    private static String describe(UriRouteMatch<?, ?> match) {
        return match.routeInfo.httpMethodName + ' ' + match.routeInfo.declaringType.name + '#' + match.routeInfo.targetMethod.methodName + ' ' + match.variableValues
    }

    private static String matchedBy(DefaultRouter router, HttpRequest<?> request) {
        def match = router.findClosest(request)
        def field = match.getClass().getDeclaredField('matchInfo')
        field.setAccessible(true)
        return field.get(match).getClass().simpleName
    }

    private static List<String> parse(RoutePlan plan, String path) {
        RouteSlot[] slots = plan.slots()
        List<String> matched = []
        plan.match(path, { int slot, String p, int[] spans ->
            matched << slots[slot].controller().methodName() + ':' + slots[slot].httpMethodName()
        } as RouteCandidateSink)
        return matched
    }

    /**
     * A plan with other compatibility facts.
     */
    private static final class DelegatingPlan implements RoutePlan {
        @Delegate(excludes = ['fingerprint', 'abiVersion'])
        private final RoutePlan plan
        private final String fingerprint
        private final int abiVersion

        DelegatingPlan(RoutePlan plan, String fingerprint, int abiVersion) {
            this.plan = plan
            this.fingerprint = fingerprint
            this.abiVersion = abiVersion
        }

        @Override
        String fingerprint() {
            return fingerprint
        }

        @Override
        int abiVersion() {
            return abiVersion
        }
    }
}
