package example.security.services

import io.micronaut.security.authentication.providers.PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import javax.annotation.PostConstruct
import javax.inject.Singleton

@Singleton
class BCryptPasswordEncoderService implements PasswordEncoder {
    org.pac4j.core.credentials.password.PasswordEncoder passwordEncoder

    @PostConstruct
    void initialize() {
        this.passwordEncoder = new org.pac4j.core.credentials.password.SpringSecurityPasswordEncoder(new BCryptPasswordEncoder())
    }

    String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword)
    }

    @Override
    boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword)
    }
}
