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
package io.micronaut.http.client.docs.basics;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CustomHttpMethod;
import io.micronaut.http.annotation.Get;

@Controller("/webdav")
public class WebdavController {
    @Get
    public String get() {
        return "GET easy";
    }

    @CustomHttpMethod(method = "PROPFIND")
    public String propfind() {
        return "PROPFIND easy";
    }

    @CustomHttpMethod(method = "PROPPATCH")
    public String proppatch() {
        return "PROPPATCH easy";
    }

    @CustomHttpMethod(method = "PROPFIND", value = "/{name}")
    public String propfind(String name) {
        return "PROPFIND " + name;
    }

    @CustomHttpMethod(method = "REPORT", value = "/{name}")
    public Message report(String name) {
        return new Message("REPORT " + name);
    }

    @CustomHttpMethod(method = "LOCK")
    public Message report(@Body Message name) {
        return new Message("LOCK " + name.getText());
    }
}