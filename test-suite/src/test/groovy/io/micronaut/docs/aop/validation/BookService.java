/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.aop.validation;

// tag::imports[]
import io.micronaut.validation.Validated;
import javax.inject.Singleton;
import javax.validation.constraints.NotBlank;
import java.util.*;
// end::imports[]

/**
 * @author graemerocher
 * @since 1.0
 */

// tag::class[]
@Singleton
@Validated // <1>
public class BookService {

    private Map<String, String> authorsByTitle = new LinkedHashMap<>();

    public String getAuthor(@NotBlank String title) { // <2>
        return authorsByTitle.get(title);
    }

    public void addBook(@NotBlank String author, @NotBlank String title) {
        authorsByTitle.put(title, author);
    }
}
// end::class[]