package io.micronaut.docs.annotation.requestattributes

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RequestAttributeSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    void "test sender attributes"() throws Exception {
        when:
        StoryClient client = embeddedServer.getApplicationContext().getBean(StoryClient)
        StoryClientFilter filter = embeddedServer.getApplicationContext().getBean(StoryClientFilter)

        Story story = Mono.from(client.getById("jan2019")).block()

        then:
        null != story

        Map<String, Object> attributes = filter.getLatestRequestAttributes()
        null != attributes
        "jan2019" == attributes.get("story-id")
        "storyClient" == attributes.get("client-name")
        "1" == attributes.get("version")
    }
}
