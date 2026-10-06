/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.docs.sse;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

@Requires(property = "spec.name", value = "AsyncSseClientSpec")
@Controller("/mcp")
public class McpController {

    @Post(produces = MediaType.TEXT_EVENT_STREAM)
    HttpResponse<Publisher<Event<String>>> message(@Body String message) {
        return HttpResponse.<Publisher<Event<String>>>ok(Flux.just(Event.of("progress"), Event.of("done")))
            .header("Mcp-Session-Id", "abc-123");
    }
}
