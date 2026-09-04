package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class PropertyAnnotationSites {

    @SiteValue("field")
    private String conflicting;

    private String parameterOnly;

    @SiteValue("declared")
    private String declared;

    @Tag("field")
    private String repeated;

    @SiteValue("getter")
    public String getConflicting() {
        return conflicting;
    }

    @SiteValue("setter")
    public void setConflicting(@SiteValue("parameter") String conflicting) {
        this.conflicting = conflicting;
    }

    public String getParameterOnly() {
        return parameterOnly;
    }

    public void setParameterOnly(@SiteValue("parameter") String parameterOnly) {
        this.parameterOnly = parameterOnly;
    }

    public String getDeclared() {
        return declared;
    }

    public void setDeclared(String declared) {
        this.declared = declared;
    }

    @Tag("getter")
    public String getRepeated() {
        return repeated;
    }

    @Tag("setter")
    public void setRepeated(@Tag("parameter") String repeated) {
        this.repeated = repeated;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface SiteValue {
        String value();
    }
}
