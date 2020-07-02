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
package io.micronaut.docs.context.annotation.primary;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class PrimarySpec {

    private static EmbeddedServer embeddedServer;

    private static RxHttpClient rxClient;


    @BeforeClass
    public static void setup(){
        embeddedServer = ApplicationContext.run(EmbeddedServer.class, new HashMap<String, Object>() {{
            put("spec.name", "primaryspec");
            put("spec.lang", "java");
        }}, Environment.TEST);
        rxClient = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());
    }
    @AfterClass
    public static void teardown(){
        if(rxClient != null){
            rxClient.close();
        }
        if(embeddedServer != null){
            embeddedServer.close();
        }
    }

    @Test
    public void testPrimaryAnnotatedBeanIsInjectedWhenMultipleOptionsExist() {
        assertEquals(embeddedServer.getApplicationContext().getBeansOfType(ColorPicker.class).size(), 2);

        HttpResponse<String> rsp = rxClient.toBlocking().exchange(HttpRequest.GET("/testPrimary"), String.class);

        assertEquals(rsp.status(), HttpStatus.OK);
        assertEquals(rsp.body(), "green");
    }
}
