package example.api.v1;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import javax.validation.Valid;

@Validated
public interface MailOperation {
    @Post("/send")
    HttpResponse send(@Valid @Body Email email);
}
