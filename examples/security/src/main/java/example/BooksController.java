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

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.validation.Validated;
import io.reactivex.Single;

import javax.inject.Singleton;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller("/books")
@Singleton
@Validated
public class BooksController {

    protected final BooksRepository booksRepository;

    public BooksController(BooksRepository booksRepository) {
        this.booksRepository = booksRepository;
    }

    @Get("/grails")
    public List<Book> grails() {
        return booksRepository.findAllByGenre(BookGenre.GRAILS);
    }

    @Get("/groovy")
    public List<Book> groovy() {
        return booksRepository.findAllByGenre(BookGenre.GROOVY);
    }
}
