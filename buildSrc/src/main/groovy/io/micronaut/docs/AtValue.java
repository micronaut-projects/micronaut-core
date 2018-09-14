package io.micronaut.docs;

public class AtValue {

    private String type;
    private String fieldName;

    public AtValue() {}


    public AtValue(String type, String fieldName) {
        this.type = type;
        this.fieldName = fieldName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type.replaceAll(" " , "") : type;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName != null ? fieldName.replaceAll(" " , "") : fieldName;
    }
}
