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
package io.micronaut.docs.streaming;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.RxStreamingHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.Assert.*;

public class HeadlineControllerSpec {

    // tag::streamingClient[]
    @Test
    public void testClientAnnotationStreaming() throws Exception {
        try( EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class) ) {
            HeadlineClient headlineClient = embeddedServer
                                                .getApplicationContext()
                                                .getBean(HeadlineClient.class); // <1>

            Maybe<Headline> firstHeadline = headlineClient.streamHeadlines().firstElement(); // <2>

            Headline headline = firstHeadline.blockingGet(); // <3>

            assertNotNull( headline );
            assertTrue( headline.getText().startsWith("Latest Headline") );
        }
    }
    // end::streamingClient[]


    @Test
    public void testStreamingClient() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxStreamingHttpClient client = embeddedServer.getApplicationContext().createBean(RxStreamingHttpClient.class, embeddedServer.getURL());


        // tag::streaming[]
        Flowable<Headline> headlineStream = client.jsonStream(GET("/streaming/headlines"), Headline.class); // <1>
        CompletableFuture<Headline> future = new CompletableFuture<>(); // <2>
        headlineStream.subscribe(new Subscriber<Headline>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1); // <3>
            }

            @Override
            public void onNext(Headline headline) {
                System.out.println("Received Headline = " + headline.getText());
                future.complete(headline); // <4>
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t); // <5>
            }

            @Override
            public void onComplete() {
                // no-op // <6>
            }
        });
        // end::streaming[]
        try {
            Headline headline = future.get(3, TimeUnit.SECONDS);
            assertTrue(headline.getText().startsWith("Latest Headline"));

        } catch (Throwable e) {
            fail("Asynchronous error occurred: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }


        embeddedServer.stop();
        client.stop();
    }
}
