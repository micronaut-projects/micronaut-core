package io.micronaut.jackson.modules.testcase;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class Notification {

    private Long id;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "templateType")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = EmailTemplate.class, name = "email"),
            @JsonSubTypes.Type(value = SmsTemplate.class, name = "sms")
    })
    private MessageTemplate template;

    public Notification(Long id, MessageTemplate template) {
        this.id = id;
        this.template = template;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MessageTemplate getTemplate() {
        return template;
    }

    public void setTemplate(MessageTemplate template) {
        this.template = template;
    }
}
