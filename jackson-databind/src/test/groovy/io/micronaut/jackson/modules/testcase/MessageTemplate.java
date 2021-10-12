package io.micronaut.jackson.modules.testcase;

public abstract class MessageTemplate {
    private String templateType;

    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }
}
