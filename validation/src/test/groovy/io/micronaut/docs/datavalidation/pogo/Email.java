package io.micronaut.docs.datavalidation.pogo;

import javax.validation.constraints.NotBlank;

public class Email {

    @NotBlank // <1>
    String subject;

    @NotBlank // <1>
    String recipient;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
}
