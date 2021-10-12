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
package io.micronaut.inject.bind;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class Author {
    private String name;
    private Set<Book> books;
    private Optional<URI> website = Optional.empty();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Book> getBooks() {
        return books;
    }

    public void setBooks(Set<Book> books) {
        this.books = books;
    }

    public Optional<URI> getWebsite() {
        return website;
    }

    public void setWebsite(Optional<URI> website) {
        this.website = website;
    }
}
