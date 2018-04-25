package io.micronaut.security.jwt;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.Security;
import java.util.Optional;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class KeyPairProvider {

    private static final Logger LOG = LoggerFactory.getLogger(KeyPairProvider.class);

    /**
     *
     * @param pemPath Full path to PEM file.
     * @return returns KeyPair if successfully for PEM files.
     */
    public static Optional<KeyPair> keyPair(String pemPath) {
        // Load BouncyCastle as JCA provider
        Security.addProvider(new BouncyCastleProvider());

        // Parse the EC key pair
        PEMParser pemParser;
        try {
            pemParser = new PEMParser(new InputStreamReader(new FileInputStream(pemPath)));
            PEMKeyPair pemKeyPair = (PEMKeyPair) pemParser.readObject();

            // Convert to Java (JCA) format
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            KeyPair keyPair = converter.getKeyPair(pemKeyPair);
            pemParser.close();

            return Optional.of(keyPair);

        } catch (FileNotFoundException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("file not found: {}", pemPath);
            }

        } catch (PEMException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("PEMException {}", e.getMessage());
            }

        } catch (IOException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("IOException {}", e.getMessage());
            }
        }
        return Optional.empty();
    }
}
