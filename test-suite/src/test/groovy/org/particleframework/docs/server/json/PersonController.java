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

import io.reactivex.Maybe;
import io.reactivex.Single;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.annotation.Body;
import org.particleframework.http.annotation.Controller;
import org.particleframework.web.router.annotation.Get;
import org.particleframework.web.router.annotation.Post;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller("/people")
@Singleton
public class PersonController {

    Map<String, Person> inMemoryDatastore = new LinkedHashMap<>();

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

    @Post("/")
    Single<HttpResponse<Person>> save(@Body Single<Person> person) {
        return person.map(p -> {
                    inMemoryDatastore.put(p.getFirstName(), p);
                    return HttpResponse.created(p);
                }
        );
    }
}
