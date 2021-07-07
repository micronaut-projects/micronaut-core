package io.micronaut.http.client.bind

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import javax.annotation.Nullable


@MicronautTest
class ClientBindSpec extends Specification {

    @Inject BindClient bindClient
    @Inject ErrorContextPath errorContextPath

    void "test binding to a query value"() {
        expect:
        bindClient.queryValue("xx", Optional.of("yy")) == '[x:xx, y:yy]'
        bindClient.queryValue("xx", Optional.empty()) == '[x:xx]'
    }

    void "test binding to a path value"() {
        expect:
        bindClient.pathValue("xx", Optional.of("yy")) == '/xx/yy'
        bindClient.pathValue("xx", Optional.empty()) == '/xx'
    }

    void "test a uri variable in the context path"() {
        when:
        errorContextPath.get("test")

        then:
        def ex = thrown(HttpClientException)
        ex.message == "Failed to construct the request URI"
    }

    void "test space separated query value"() {
        given:
        List<String> values = ["xx", "yy", "zz", "what"]

        expect:
        bindClient.spaceSeparatedQueryValue(values) == "xx yy zz what"
    }

    void "test separated query values"() {
        given:
        List<String> values = ["xx", "yy"]

        expect:
        bindClient.commaSeparatedQueryValue(values) == "x=xx,yy"
        bindClient.pipeSeparatedQueryValue(values) == "x=xx|yy"
        var result = bindClient.differentSeparatedQueryValues(values, values)
        result.contains("pipeParams=xx|yy")
        result.count("pipeParams") == 1
        result.contains("commaParams=xx,yy")
        result.count("commaParams=xx,yy") == 1
    }

    void "test multi format query values"() {
        given:
        List<String> values = ["aa", "bb", "cc"]

        expect:
        bindClient.multiQueryValue(values) == "x=aa&x=bb&x=cc"
    }

    void "test deep object format query values"() {
        given:
        var house = new House("white", 2, 120)

        expect:
        var result = bindClient.deepObjectQueryValue(house)
        result.contains("house[nFloors]=2")
        result.contains("house[color]=white")
        result.contains("house[area]=120.0")
    }

    void "test bind to method"() {
        expect:
        bindClient.authorizedRequest() == "name=admin"
        1
    }

    @Client("/bind")
    static interface BindClient {

        @Get("/queryValue")
        String queryValue(@QueryValue String x, @QueryValue Optional<String> y)

        @Get("/pathValue{/x}{/y}")
        String pathValue(String x, Optional<String> y)

        @Get("/queryFormat")
        String spaceSeparatedQueryValue(@QueryValue(format=QueryValue.Format.SSV) List<String> x)

        @Get("/queryIdentity")
        String commaSeparatedQueryValue(@QueryValue(value="x", format=QueryValue.Format.CSV) List<String> params);

        @Get("/queryIdentity")
        String pipeSeparatedQueryValue(@QueryValue(value="x", format=QueryValue.Format.PIPES) List<String> params);

        @Get("/queryIdentity")
        String differentSeparatedQueryValues(
                @QueryValue(format=QueryValue.Format.PIPES) List<String> pipeParams,
                @QueryValue(format=QueryValue.Format.CSV) List<String> commaParams
        )

        @Get("/queryIdentity")
        String multiQueryValue(@QueryValue(format=QueryValue.Format.MULTI) List<String> x)

        @Get("/queryIdentity")
        String deepObjectQueryValue(@QueryValue(format=QueryValue.Format.DEEP_OBJECT) House house)

        @Get("/queryIdentity")
        @SimpleTestAuthorization
        String authorizedRequest()
    }

    @Client(value = "/", path = "/{bar}")
    static interface ErrorContextPath {
        @Get
        String get(@PathVariable String bar)
    }

    @Controller("/bind")
    static class BindController {

        @Get("/queryValue{?params*}")
        String queryValue(Map<String, String> params) {
            params.toString()
        }

        @Get("/pathValue{+path}")
        String pathValue(String path) {
            path
        }

        @Get("/queryFormat{?x}")
        String queryFormat(@QueryValue @Nullable String x) {
            return x
        }

        @Get("/queryIdentity")
        String queryIdentity(HttpRequest<?> request) {
            return request.getUri().getQuery()
        }
    }
}

@Introspected
class House {
    String color
    Integer nFloors
    Float area

    House(String color, Integer nFloors, Float area) {
        this.color = color
        this.nFloors = nFloors
        this.area = area
    }

    String getColor() {
        return color
    }

    Integer getNFloors() {
        return nFloors
    }

    Float getArea() {
        return area
    }
}
