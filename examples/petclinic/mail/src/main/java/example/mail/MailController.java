package example.mail;

import org.particleframework.http.annotation.Body;
import org.particleframework.http.annotation.Controller;
import org.particleframework.http.annotation.Get;
import org.particleframework.http.annotation.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;

@Controller
@Singleton
public class MailController {

    private static final Logger log = LoggerFactory.getLogger(MailController.class);

    @Post("/send")
    public String index(@Body Email email) {
        log.info(email.toString());
        return "Hello " + email.getRecipient();
    }
}