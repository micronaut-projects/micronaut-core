/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.docs.server.routes;

// tag::imports[]
import okhttp3.*;
import org.junit.*;
import org.particleframework.context.ApplicationContext;
import org.particleframework.runtime.server.EmbeddedServer;
import java.net.URL;

import static org.junit.Assert.*;
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class MessageControllerTest {



    // tag::setup[]
    private static EmbeddedServer server;

    @BeforeClass // <1>
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
    }

    @AfterClass // <1>
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
    }
    // end::setup[]

    // tag::test[]
    @Test
    public void testHello() throws Exception {
        OkHttpClient client = new OkHttpClient();

        Request.Builder request = new Request.Builder()
                                        .url(new URL(server.getURL(), "/message/hello/John")); // <2>
        Response response = client.newCall(request.build()).execute();
        ResponseBody body = response.body();
        assertNotNull(body);
        assertEquals( // <3>
                body.string(),
                "Hello John!"
        );
    }
    // end::test[]
}
