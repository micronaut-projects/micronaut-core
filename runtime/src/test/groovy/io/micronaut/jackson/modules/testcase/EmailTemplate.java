package io.micronaut.jackson.modules.testcase;

public class EmailTemplate extends MessageTemplate {
    private String subject;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

}
