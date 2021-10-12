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
package io.micronaut.docs.context.annotation.primary

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = 'spec.name', value = 'primaryspec')
//tag::clazz[]
@Controller("/test")
public class TestController {

    protected final ColorPicker colorPicker;

    public TestController(ColorPicker colorPicker) { // <1>
        this.colorPicker = colorPicker;
    }

    @Get
    public String index() {
        return colorPicker.color();
    }
}
//end::clazz[]