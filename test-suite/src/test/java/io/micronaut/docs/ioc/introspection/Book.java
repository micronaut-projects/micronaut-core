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
package io.micronaut.docs.ioc.introspection;

// tag::imports[]
import io.micronaut.core.annotation.Introspected;
// end::imports[]

// tag::class[]
@Introspected
class Book {
    private String title;
    private String author = "Ursula Le Guin";

    @Introspected.Property("book_title")
    public String title() {
        return title;
    }

    @Introspected.Property("book_title")
    public void title(String title) {
        this.title = title;
    }

    @Introspected.Property(
        value = "author_name",
        accessKind = Introspected.Property.Access.READ
    )
    public String author() {
        return author;
    }
}
// end::class[]
