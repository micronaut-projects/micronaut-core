package io.micronaut

import geb.Page
import geb.spock.GebSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.upload.browser.CreatePage
import io.micronaut.upload.browser.FileEmptyPage
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CookiesSpec extends GebSpec {

    Map<String, Object> getConfiguration() {
        Map<String, Object> m = [:]
        if (specName) {
            m['spec.name'] = specName
        }
        m
    }

    String getSpecName() {
        'CookiesSpec'
    }

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, configuration)

    def "it is possible to set multiple cookies"() {
        given:
        browser.baseUrl = "http://localhost:${embeddedServer.port}"

        expect:
        browser.driver.manage().cookies.isEmpty()

        when:
        browser.go '/cookies'

        then:
        browser.title == 'Title of the document'

        and:
        browser.driver.manage().cookies.size() == 2
        browser.driver.manage().getCookieNamed('A').value == 'B'
        browser.driver.manage().getCookieNamed('C').value == 'D'
    }
    
    @Requires(property = 'spec.name', value = 'CookiesSpec')
    @Controller("/cookies")
    static class CookiesController {

        @Produces(MediaType.TEXT_HTML)
        @Get
        HttpResponse index() {
            Cookie a = Cookie.of('A', 'B')
            Cookie c = Cookie.of('C', 'D')
            Set<Cookie> cookies = [a, c] as Set<Cookie>
            HttpResponse.ok('''
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Title of the document</title>
</head>
<body>
Content of the document......
</body>
</html>
''').cookies(cookies)
        }
    }
}