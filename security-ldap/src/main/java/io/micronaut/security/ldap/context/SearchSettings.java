package io.micronaut.security.ldap.context;

public interface SearchSettings {

    boolean isSubtree();
    String getBase();
    String getFilter();
    Object[] getArguments();
    String[] getAttributes();

}
