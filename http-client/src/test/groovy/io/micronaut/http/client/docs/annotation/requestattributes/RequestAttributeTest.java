/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.docs.annotation.requestattributes;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class RequestAttributeTest {

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
