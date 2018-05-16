package example.mail;

import example.api.v1.Email;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;

@Controller("/${mail.api.version}/mail")
public class MailController {

    private static final Logger log = LoggerFactory.getLogger(MailController.class);

    @Inject
    EmailService emailService;

    @Post("/send")
    public HttpResponse send(@Valid @Body Email email) {
        log.info(email.toString());
        if ( emailService == null ) {
            log.warn("Email service not injected");
            return HttpResponse.serverError();
        }
        emailService.send(email);
        return HttpResponse.ok();
    }
}