package io.micronaut.security.ldap.context;

import io.micronaut.core.convert.value.ConvertibleValues;

import javax.naming.directory.Attributes;

public class LdapSearchResult {

    private ConvertibleValues<Object> attributes;
    private String dn;
    private String username;

    public LdapSearchResult(Attributes attributes, String dn) {
        this.setAttributes(attributes);
        this.setDn(dn);
    }

    public ConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = new AttributesConvertibleValues(attributes);
    }

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
