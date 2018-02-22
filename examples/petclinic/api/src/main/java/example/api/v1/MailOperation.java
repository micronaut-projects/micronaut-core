package example.api.v1;

import org.particleframework.http.HttpResponse;
import org.particleframework.http.annotation.Body;
import org.particleframework.http.annotation.Post;
import org.particleframework.validation.Validated;
import javax.validation.Valid;

@Validated
public interface MailOperation {
    @Post("/send")
    HttpResponse send(@Valid @Body Email email);
}
