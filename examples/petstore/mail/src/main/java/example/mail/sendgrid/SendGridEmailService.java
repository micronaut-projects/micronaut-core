package example.mail.sendgrid;

import com.sendgrid.*;
import example.api.v1.Email;
import example.mail.EmailService;
import example.mail.MailController;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;

@Singleton
@Requires(property = "sendgrid.apiKey")
public class SendGridEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(MailController.class);

    @Value("${sendgrid.apiKey}")
    String apiKey;

    @Value("${sendgrid.fromEmail}")
    String fromEmail;

    protected Content contentOfEmail(Email email) {
        if ( email.getTextBody() != null ) {
            return new Content("text/plain", email.getTextBody());
        }
        if ( email.getHtmlBody() != null ) {
            return new Content("text/html", email.getHtmlBody());
        }
        return null;
    }

    @Override
    public void send(Email email) {

        Personalization personalization = new Personalization();
        personalization.setSubject(email.getSubject());

        com.sendgrid.Email to = new com.sendgrid.Email(email.getRecipient());
        personalization.addTo(to);

        if ( email.getCc() != null ) {
            for ( String cc : email.getCc() ) {
                com.sendgrid.Email ccEmail = new com.sendgrid.Email();
                ccEmail.setEmail(cc);
                personalization.addCc(ccEmail);
            }
        }

        if ( email.getBcc() != null ) {
            for ( String bcc : email.getBcc() ) {
                com.sendgrid.Email bccEmail = new com.sendgrid.Email();
                bccEmail.setEmail(bcc);
                personalization.addBcc(bccEmail);
            }
        }

        Mail mail = new Mail();
        com.sendgrid.Email from = new com.sendgrid.Email();
        from.setEmail(fromEmail);
        mail.setFrom(from);
        mail.addPersonalization(personalization);
        Content content = contentOfEmail(email);
        mail.addContent(content);

        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            log.info("Status Code: {}", String.valueOf(response.getStatusCode()));
            log.info("Body: {}", response.getBody());
            for ( String key : response.getHeaders().keySet()) {
                String value = response.getHeaders().get(key);
                log.info("Response Header {} => {}", key, value);
            }
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }
    }
}
