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
package example;

import okhttp3.*;
import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.http.HttpStatus;
import org.particleframework.runtime.server.EmbeddedServer;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.*;
/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class UpperCaseTitleFunctionTest {

    @Test
    public void testFunction() throws IOException {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        OkHttpClient client = new OkHttpClient();
        String data = "{\"title\":\"The Stand\"}";
        Request.Builder request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/uppercase"))
                .post(createBody(data));

        Response response = client.newCall(request.build()).execute();

        assertEquals(response.code(), HttpStatus.OK.getCode());
        ResponseBody body = response.body();
        assertNotNull(body);
        assertEquals(body.string(), "{\"title\":\"THE STAND\"}");

        embeddedServer.stop();
    }

    private RequestBody createBody(String data) {
        return RequestBody.create( MediaType.parse(org.particleframework.http.MediaType.APPLICATION_JSON), data);
    }
}
