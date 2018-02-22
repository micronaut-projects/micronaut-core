package example.mail;

import example.api.v1.Email;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.annotation.Body;
import org.particleframework.http.annotation.Controller;
import org.particleframework.http.annotation.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.validation.Valid;

@Controller("/${mail.api.version}/mail")
@Singleton
public class MailController {

    private static final Logger log = LoggerFactory.getLogger(MailController.class);

    @Post("/send")
    public HttpResponse send(@Valid @Body Email email) {
        log.info(email.toString());
        return HttpResponse.ok();
    }
}