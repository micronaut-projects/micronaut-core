package io.micronaut.http.client

import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class HttpClientCloseSpec extends Specification {
    void "confirm HttpClient can be stopped"() {
        given:
        HttpClient client = HttpClient.create(new URL("http://localhost"))

        expect:
        client.isRunning()

        when:
        client.stop()

        then:
        new PollingConditions().eventually {
            !client.isRunning()
        }

        when:
        client.start()

        then:
        new PollingConditions().eventually {
            client.isRunning()
        }

        cleanup:
        client.close()

    }
}
