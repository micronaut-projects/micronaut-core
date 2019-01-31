package io.micronaut.views.model.security

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import io.micronaut.security.authentication.UserDetails

@InheritConstructors
@CompileStatic
class UserDetailsEmail extends UserDetails  {
    String email
}
