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
package io.micronaut.docs.config.env;

import java.net.URI;
import java.net.URISyntaxException;

// tag::eachProperty[]
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.EachProperty;

@EachProperty("test.datasource")  // <1>
public class DataSourceConfiguration {

    private final String name;
    private URI url = new URI("localhost");

    public DataSourceConfiguration(@Parameter String name) // <2>
            throws URISyntaxException {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public URI getUrl() { // <3>
        return url;
    }

    public void setUrl(URI url) {
        this.url = url;
    }
}
// end::eachProperty[]