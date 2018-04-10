package example.security.providers

import example.security.services.UserGormService
import example.security.services.UserRoleGormService
import groovy.transform.CompileStatic
import io.micronaut.security.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.controllers.UsernamePassword
import io.micronaut.security.passwordencoder.PasswordEncoder

import javax.inject.Singleton

@CompileStatic
@Singleton
class DbService implements AuthenticationProvider {

    protected final UserGormService userGormService
    protected final UserRoleGormService userRoleGormService
    protected final PasswordEncoder passwordEncoder

    DbService(UserGormService userGormService,
              UserRoleGormService userRoleGormService,
              PasswordEncoder passwordEncoder) {
        this.userGormService = userGormService
        this.userRoleGormService = userRoleGormService
        this.passwordEncoder = passwordEncoder
    }

    @Override
    AuthenticationResponse authenticate(AuthenticationRequest req) {
        if ( req instanceof UsernamePassword ) {
            UsernamePassword usernamePassword = (UsernamePassword) req

            example.security.domain.User user = userGormService.findByUsername(req.username)
            if (!user) {
                return new AuthenticationFailed()
            }
            if (!user.enabled || user.accountExpired || user.accountLocked || user.passwordExpired) {
                return new AuthenticationFailed()
            }

            if ( passwordEncoder.matches(usernamePassword.password, user.password) ) {
                List<String> authorities = userRoleGormService.findAuthoritiesByUsername(user.username)
                return new UserDetails(user.username, authorities)
            }
        }
        return new AuthenticationFailed()
    }
}
