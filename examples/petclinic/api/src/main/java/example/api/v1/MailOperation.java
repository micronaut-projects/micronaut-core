package example.api.v1;

import org.particleframework.http.HttpResponse;
import org.particleframework.http.annotation.Body;
import org.particleframework.http.annotation.Post;

public interface MailOperation extends  HealthStatusOperation {
    @Post("/mail/send")
    HttpResponse send(@Body Email email);
}
