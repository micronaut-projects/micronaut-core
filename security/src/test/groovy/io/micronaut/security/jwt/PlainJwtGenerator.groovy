package io.micronaut.security.jwt

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWSAlgorithm
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.jwt.config.CryptoAlgorithm
import io.micronaut.security.jwt.config.JwtGeneratorConfiguration
import io.micronaut.security.jwt.encryption.JwtGeneratorEncryptionConfiguration
import io.micronaut.security.jwt.generator.JwtTokenGenerator
import io.micronaut.security.jwt.generator.claims.JWTClaimsSetGenerator
import io.micronaut.security.jwt.signature.JwtGeneratorSignatureConfiguration

trait PlainJwtGenerator {
    String plainJwt(String user, List<String> roles, Integer expiration = 3600) {
        new JwtTokenGenerator(new MockJwtGeneratorSignatureConfiguration(),
                new MockJwtGeneratorEncryptionConfiguration(),
                new JWTClaimsSetGenerator(new MockJwtGeneratorConfiguration())
        ).generateToken(new UserDetails(user, roles), 3600)
    }
}
class MockJwtGeneratorSignatureConfiguration implements JwtGeneratorSignatureConfiguration {

    @Override
    boolean isEnabled() {
        return false
    }

    @Override
    CryptoAlgorithm getType() {
        return null
    }

    @Override
    JWSAlgorithm getJwsAlgorithm() {
        return null
    }

    @Override
    String getSecret() {
        return null
    }

    @Override
    String getPemPath() {
        return null
    }
}
class MockJwtGeneratorEncryptionConfiguration implements JwtGeneratorEncryptionConfiguration {
    @Override
    boolean isEnabled() {
        return false
    }

    @Override
    CryptoAlgorithm getType() {
        return null
    }

    @Override
    String getPemPath() {
        return null
    }

    @Override
    JWEAlgorithm getJweAlgorithm() {
        return null
    }

    @Override
    EncryptionMethod getEncryptionMethod() {
        return null
    }

    @Override
    String getSecret() {
        return null
    }
}
class MockJwtGeneratorConfiguration implements JwtGeneratorConfiguration {

    @Override
    Integer getAccessTokenExpiration() {
        return null
    }

    @Override
    Integer getRefreshTokenExpiration() {
        return null
    }

    @Override
    String getRolesClaimName() {
        return 'roles'
    }
}