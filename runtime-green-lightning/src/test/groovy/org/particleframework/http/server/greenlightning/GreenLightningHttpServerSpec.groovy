package org.particleframework.http.server.greenlightning

import org.particleframework.context.ApplicationContext
import org.particleframework.http.annotation.Controller
import org.particleframework.runtime.ParticleApplication
import org.particleframework.runtime.server.EmbeddedServer
import org.particleframework.web.router.annotation.Get
import org.particleframework.web.router.annotation.Put
import spock.lang.Specification

class GreenLightningHttpServerSpec extends Specification {

    void "test Particle server running"() {
        when:
        ApplicationContext applicationContext = ParticleApplication.run()
        int port = applicationContext.getBean(EmbeddedServer).getPort()

        then:
        new URL("http://localhost:${port}/person/Fred").getText(readTimeout: 3000) == "Person Named Fred"

        cleanup:
        applicationContext?.stop()
    }
/*
    void "test Particle server running again"() {
        when:
        ApplicationContext applicationContext = ParticleApplication.run()

        then:
        new URL("http://localhost:8080/person/Fred").getText(readTimeout: 3000) == "Person Named Fred"

        cleanup:
        applicationContext?.stop()
    }

    void "test Particle server on different port"() {
        when:
        int newPort = SocketUtils.findAvailableTcpPort()
        ApplicationContext applicationContext = ParticleApplication.run('-port', newPort.toString())

        then:
        new URL("http://localhost:$newPort/person/Fred").getText(readTimeout: 3000) == "Person Named Fred"

        cleanup:
        applicationContext?.stop()
    }

    void "test bind method argument from request parameter"() {
        when:
        int newPort = SocketUtils.findAvailableTcpPort()
        ApplicationContext applicationContext = ParticleApplication.run('-port', newPort.toString())

        then:
        new URL("http://localhost:$newPort/person/another/job?id=10").getText(readTimeout: 3000) == "JOB ID 10"

        cleanup:
        applicationContext?.stop()
    }

    void "test bind method argument from request parameter when parameter missing"() {
        when: "A required request parameter is missing"
        int newPort = SocketUtils.findAvailableTcpPort()
        ApplicationContext applicationContext = ParticleApplication.run('-port', newPort.toString())
        new URL("http://localhost:$newPort/person/another/job").getText(readTimeout: 3000)

        then: "A 404 is returned"
        thrown(FileNotFoundException)

        cleanup:
        applicationContext?.stop()
    }
*/
    @Controller
    static class PersonController {

        @Get('/{name}')
        String name(String name) {
            "Person Named $name"
        }

        @Put('/job/{name}')
        void doWork(String name) {
            println 'doing work'
        }

        @Get('/another/job')
        String doMoreWork(int id) {
            "JOB ID $id"
        }
    }
}
