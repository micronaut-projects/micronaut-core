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
package io.micronaut.docs.client.filter;

import io.micronaut.context.env.Environment;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.test.annotation.MicronautTest;
import junit.framework.TestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = Environment.GOOGLE_COMPUTE)
public class GoogleAuthFilterSpec {

    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    void testApplyGoogleAuthFilter() {
        HttpClientException e = Assertions.assertThrows(HttpClientException.class, () ->
                client.exchange("/google-auth/api/test").blockingFirst()
        );
        String message = e.getMessage();
        assertTrue(
                message.contains("metadata: nodename nor servname provided") ||
                        message.contains("metadata: Temporary failure in name resolution") ||
                        message.contains("metadata: Name or service not known")
        );

    }
}
