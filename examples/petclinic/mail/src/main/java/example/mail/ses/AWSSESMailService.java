package example.mail.ses;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.IOException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import example.api.v1.Email;
import example.mail.EmailService;
import example.mail.MailController;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Requires(beans = AWSCredentialsProviderService.class)
public class AWSSESMailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(MailController.class);

    @Value("${aws.ses.region}")
    String awsRegion;

    @Value("${aws.sourceEmail}")
    String sourceEmail;

    @Inject
    AWSCredentialsProvider awsCredentialsProvider;

    private Body bodyOfEmail(Email email) {
        if (email.getHtmlBody() != null) {
            Content htmlBody = new Content().withData(email.getHtmlBody());
            return new Body().withHtml(htmlBody);
        }
        if (email.getTextBody() != null) {
            Content textBody = new Content().withData(email.getTextBody());
            return new Body().withHtml(textBody);
        }
        return new Body();
    }

    @Override
    public void send(Email email) {

        if ( awsCredentialsProvider == null ) {
            log.warn("AWS Credentials provider not configured");
            return;
        }

        Destination destination = new Destination().withToAddresses(email.getRecipient());
        if ( email.getCc() != null ) {
            destination = destination.withCcAddresses(email.getCc());
        }
        if ( email.getBcc() != null ) {
            destination = destination.withBccAddresses(email.getBcc());
        }
        Content subject = new Content().withData(email.getSubject());
        Body body = bodyOfEmail(email);
        Message message = new Message().withSubject(subject).withBody(body);

        SendEmailRequest request = new SendEmailRequest()
                .withSource(sourceEmail)
                .withDestination(destination)
                .withMessage(message);

        if ( email.getReplyTo() != null ) {
            request = request.withReplyToAddresses();
        }

        try {
            log.info("Attempting to send an email through Amazon SES by using the AWS SDK for Java...");

            AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                    .withCredentials(awsCredentialsProvider)
                    .withRegion(awsRegion)
                    .build();

            client.sendEmail(request);
            log.info("Email sent!");

        } catch (Exception ex) {
            log.warn("The email was not sent.");
            log.warn("Error message: " + ex.getMessage());
        }
    }
}

