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
package org.particleframework.docs.server.json;

import io.reactivex.*;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.annotation.*;
import org.particleframework.web.router.annotation.*;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Controller("/people")
@Singleton
public class PersonController {

    Map<String, Person> inMemoryDatastore = new LinkedHashMap<>();
// end::class[]

    @Get("/")
    Collection<Person> index() {
        return inMemoryDatastore.values();
    }

    @Get("/{name}")
    Maybe<Person> get(String name) {
        if(inMemoryDatastore.containsKey(name)) {
            return Maybe.just(inMemoryDatastore.get(name));
        }
        return Maybe.empty();
    }

    // tag::single[]
    @Post("/")
    Single<HttpResponse<Person>> save(@Body Single<Person> person) { // <1>
        return person.map(p -> {
                    inMemoryDatastore.put(p.getFirstName(), p); // <2>
                    return HttpResponse.created(p); // <3>
                }
        );
    }
    // end::single[]

    // tag::future[]
    CompletableFuture<HttpResponse<Person>> saveFuture(@Body CompletableFuture<Person> person) {
        return person.thenApply(p -> {
                    inMemoryDatastore.put(p.getFirstName(), p);
                    return HttpResponse.created(p);
                }
        );
    }
    // end::future[]
}
