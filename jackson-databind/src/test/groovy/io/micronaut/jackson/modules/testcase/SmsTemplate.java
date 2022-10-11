package io.micronaut.jackson.modules.testcase;

public class SmsTemplate extends MessageTemplate {
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

}
