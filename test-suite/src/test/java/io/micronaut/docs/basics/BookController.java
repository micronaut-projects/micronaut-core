package io.micronaut.docs.basics;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;

@Controller("/amazon")
public class BookController {

    @Post(value = "/book/{title}", consumes = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED})
    @Status(HttpStatus.CREATED)
    Book save(String title) {
        return new Book(title);
    }
}
