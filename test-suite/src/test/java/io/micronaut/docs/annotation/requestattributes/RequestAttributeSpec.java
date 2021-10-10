package io.micronaut.docs.annotation.requestattributes;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class RequestAttributeSpec {

    @Test
    public void testSenderAttributes() throws Exception {

        try(EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class)) {
            StoryClient client = embeddedServer.getApplicationContext().getBean(StoryClient.class);
            StoryClientFilter filter = embeddedServer.getApplicationContext().getBean(StoryClientFilter.class);

            Story story = client.getById("jan2019").blockingGet();

            Assert.assertNotNull(story);

            Map<String, Object> attributes = filter.getLatestRequestAttributes();
            Assert.assertNotNull(attributes);
            Assert.assertEquals("jan2019", attributes.get("story-id"));
            Assert.assertEquals("storyClient", attributes.get("client-name"));
            Assert.assertEquals("1", attributes.get("version"));
        }
    }
}
