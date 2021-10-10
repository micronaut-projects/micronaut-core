package io.micronaut.docs.annotation.requestattributes

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class RequestAttributeSpec extends Specification {

    void "test sender attributes"() throws Exception {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        StoryClient client = embeddedServer.getApplicationContext().getBean(StoryClient)
        StoryClientFilter filter = embeddedServer.getApplicationContext().getBean(StoryClientFilter)

        Story story = client.getById("jan2019").blockingGet()

        then:
        null != story

        Map<String, Object> attributes = filter.getLatestRequestAttributes()
        null != attributes
        "jan2019" == attributes.get("story-id")
        "storyClient" == attributes.get("client-name")
        "1" == attributes.get("version")
    }
}
