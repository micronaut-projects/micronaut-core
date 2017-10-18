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
package example;

import org.particleframework.stereotype.Controller;
import org.particleframework.validation.Validated;
import org.particleframework.web.router.annotation.Get;
import org.particleframework.web.router.annotation.Post;

import javax.inject.Singleton;
import javax.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller
@Singleton
@Validated
public class BookController {

    @Get
    public List<Book> index() {
        return Arrays.asList(new Book("The Stand"), new Book("The Shining"));
    }

    @Post("/save")
    public Book saveBook(@NotBlank String title) {
        return new Book(title);
    }

}
