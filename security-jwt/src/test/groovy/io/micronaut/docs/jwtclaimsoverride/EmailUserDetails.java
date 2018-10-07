package io.micronaut.docs.jwtclaimsoverride;

import io.micronaut.security.authentication.UserDetails;

import java.util.Collection;

//tag::clazz[]
public class EmailUserDetails extends UserDetails {

    private String email;

    public EmailUserDetails(String username, Collection<String> roles) {
        super(username, roles);
    }


    public EmailUserDetails(String username, Collection<String> roles, String email) {
        super(username, roles);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
//end::clazz[]
