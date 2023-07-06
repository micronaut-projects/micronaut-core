package io.micronaut.management.endpoint.management

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.util.HttpHostResolver
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.management.endpoint.management.impl.AvailableEndpoints
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalTime

import static io.micronaut.http.HttpRequest.GET

/**
 * @author Hernán Cervera
 * @since 3.0.0
 */
class ManagementControllerSpec extends Specification {

    final String MANAGEMENT_PATH = "/management"

    @Shared EmbeddedServer server
    @Shared HttpClient client
    @Shared HttpHostResolver httpHostResolver

    void setup() {
        server = ApplicationContext.run(EmbeddedServer, [
                'endpoints.beans.enabled': true,
                'endpoints.beans.sensitive': false,
                'endpoints.routes.enabled': true,
                'endpoints.routes.sensitive': false,
                'endpoints.datecustom.enabled': true,
                'endpoints.datecustom.sensitive': false,
                'endpoints.timecustom.enabled': true,
                'endpoints.timecustom.sensitive': false,
        ])
        client = server.applicationContext.createBean(HttpClient, server.URL)
        httpHostResolver = server.applicationContext.createBean(HttpHostResolver)
    }

    void cleanup() {
        client.close()
        server.close()
    }

    void 'test the management endpoint returns a self link'() {
        when:
        def managementEndpointRequest = GET(MANAGEMENT_PATH)
        def baseRoute = getBaseRoute(managementEndpointRequest)
        def response = getManagementResponse(managementEndpointRequest)

        then:
        response.status() == HttpStatus.OK

        when:
        def result = response.body()

        then:
        def links = result.getLinks()
        def selfLinkOptional = links.get('self')

        selfLinkOptional.isPresent()
        def selfLinks = selfLinkOptional.get()
        selfLinks.size() == 1
        def selfLink = selfLinks.first()
        selfLink.href == "${baseRoute}${MANAGEMENT_PATH}"
        !selfLink.isTemplated()
    }

    void 'test the management endpoint returns built-in endpoints'() {
        when:
        def managementEndpointRequest = GET(MANAGEMENT_PATH)
        def baseRoute = getBaseRoute(managementEndpointRequest)
        def response = getManagementResponse(managementEndpointRequest)

        then:
        response.status() == HttpStatus.OK

        when:
        def result = response.body()

        then:
        def links = result.getLinks()
        def beansLinkOptional = links.get('beans')
        def routesLinkOptional = links.get('routes')

        beansLinkOptional.isPresent()
        def beansLinks = beansLinkOptional.get()
        beansLinks.size() == 1
        def beansLink = beansLinks.first()
        beansLink.href == "${baseRoute}/beans"
        !beansLink.isTemplated()

        routesLinkOptional.isPresent()
        def routesLinks = routesLinkOptional.get()
        routesLinks.size() == 1
        def routesLink = routesLinks.first()
        routesLink.href == "${baseRoute}/routes"
        !routesLink.isTemplated()
    }

    void 'test the management endpoint returns custom endpoints'() {
        when:
        def managementEndpointRequest = GET(MANAGEMENT_PATH)
        def baseRoute = getBaseRoute(managementEndpointRequest)
        def response = getManagementResponse(managementEndpointRequest)

        then:
        response.status() == HttpStatus.OK

        when:
        def result = response.body()

        then:
        def links = result.getLinks()
        def dateLinkOptional = links.get('datecustom')
        def timeLinkOptional = links.get('timecustom')

        dateLinkOptional.isPresent()
        def dateLinks = dateLinkOptional.get()
        dateLinks.size() == 1
        def dateLink = dateLinks.first()
        dateLink.href == "${baseRoute}/datecustom"
        !dateLink.isTemplated()

        timeLinkOptional.isPresent()
        def timeLinks = timeLinkOptional.get()
        timeLinks.size() == 1
        def timeLink = timeLinks.first()
        timeLink.href == "${baseRoute}/timecustom"
        !timeLink.isTemplated()
    }

    private String getBaseRoute(HttpRequest request) {
        httpHostResolver.resolve(request)
    }

    private HttpResponse<AvailableEndpoints> getManagementResponse(HttpRequest request) {
        client.exchange(request, AvailableEndpoints).blockFirst()
    }

    @Endpoint('datecustom')
    static class DateEndpoint {
        @Read
        LocalDate getCurrentDate() {
            LocalDate.now()
        }
    }

    @Endpoint('timecustom')
    static class TimeEndpoint {
        @Read
        LocalTime getCurrentTime() {
            LocalTime.now()
        }
    }
}
