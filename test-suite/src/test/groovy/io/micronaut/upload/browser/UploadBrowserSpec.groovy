package io.micronaut.upload.browser

import geb.spock.GebSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared

class UploadBrowserSpec extends GebSpec {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
                    'spec.name': UploadBrowserSpec.simpleName,
            ], Environment.TEST)

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    def "submitting a form without a file triggers an error"() {
        given:
        browser.baseUrl = "http://localhost:${embeddedServer.port}"

        when:
        go '/image/create'

        then:
        at CreatePage

        when:
        CreatePage page = browser.page CreatePage
        page.upload()

        then:
        at FileEmptyPage
    }
}
