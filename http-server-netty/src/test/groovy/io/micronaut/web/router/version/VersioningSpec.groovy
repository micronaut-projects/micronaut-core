package io.micronaut.web.router.version

import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import spock.lang.Specification

abstract class VersioningSpec extends Specification {
    static HttpRequest<?> createRequest(RouteVersioning routeVersioning,
                                        String requestVersion) {
        String uri
        switch (routeVersioning) {
            case RouteVersioning.MULTI:
                uri = '/multi'
                break
            case RouteVersioning.NONE:
                uri = '/none'
                break
            default:
                uri = '/single'
        }
        HttpRequest<?> request = HttpRequest.GET(uri)
        if (requestVersion) {
            request = request.header('X-API-Version', requestVersion)
        }
        request
    }

    static Map<String, Object> getConfiguration(String specName, boolean versioning, String defaultVersion) {
        Map<String, Object> configuration = [
                'spec.name': specName,
                "micronaut.router.versioning.header.enabled": "true"
        ]
        if (versioning) {
            configuration["micronaut.router.versioning.enabled"] = "true"
        }
        if (defaultVersion) {
            configuration["micronaut.router.versioning.default-version"] = defaultVersion
        }
        configuration
    }

    static String createDescription(boolean matches,
                                    boolean versioning,
                                    String defaultVersion,
                                    RouteVersioning routeVersioning,
                                    String requestVersion) {
        List<String> l = []
        if (versioning) {
            l << 'Versioning enabled.'
        } else {
            l << 'Versioning not enabled.'
        }
        if (defaultVersion) {
            l << 'With default version.'
        }
        l << 'It'
        if (matches) {
            l << 'matches'
        } else {
            l << 'does not match'
        }
        switch (routeVersioning) {
            case RouteVersioning.MULTI:
                l << 'multi versioned route'
                break
            case RouteVersioning.SINGLE:
                l << 'versioned route'
                break
            case RouteVersioning.NONE:
                l << 'unversioned route'
                break
        }
        if (requestVersion) {
            l << 'when request contains version'
        } else {
            l << 'when request does not contain version'
        }
        l.join(' ')
    }

    static class MultiController {

        @Version("1.0")
        @Produces(MediaType.TEXT_PLAIN)
        @Get
        String helloMoon() {
            'Hello Moon'
        }

        @Version("2.0")
        @Produces(MediaType.TEXT_PLAIN)
        @Get
        String helloWorld() {
            'Hello World'
        }
    }

    static class SingleController {

        @Version("1.0")
        @Produces(MediaType.TEXT_PLAIN)
        @Get
        String helloWorld() {
            'Hello World'
        }
    }

    static class NoneController {

        @Produces(MediaType.TEXT_PLAIN)
        @Get
        String helloWorld() {
            'Hello World'
        }
    }

    static enum RouteVersioning {
        MULTI,
        SINGLE,
        NONE
    }
}
