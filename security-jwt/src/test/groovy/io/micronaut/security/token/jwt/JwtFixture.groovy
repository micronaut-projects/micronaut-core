package io.micronaut.security.token.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

trait JwtFixture {

    SignedJWT generateSignedJWT() {
        RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate()
        RSAKey rsaPublicJWK = rsaJWK.toPublicJWK()

        // Create RSA-signer with the private key
        JWSSigner signer = new RSASSASigner(rsaJWK)

        // Prepare JWT with claims set
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build()

        new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(),
                claimsSet)
    }
}
