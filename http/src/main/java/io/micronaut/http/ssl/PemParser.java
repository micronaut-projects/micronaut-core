/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.ssl;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/**
 * Generic PEM file parser with type detection. <b>Note that this class does not defend against DER
 * parser differential vulnerabilities, so it should not be used to verify untrusted PEMs.</b>
 *
 * @param provider The security provider to use for creating cryptographic data structures
 * @param password The configured password for encrypted private keys
 */
record PemParser(
    @Nullable String provider,
    @Nullable String password
) {
    private static final String DASHES = "-----";
    private static final String START = DASHES + "BEGIN ";
    private static final String END = DASHES + "END ";

    private static final String OID_RSA = "1.2.840.113549.1.1.1";
    private static final String OID_EC = "1.2.840.10045.2.1";

    List<Object> loadPem(byte[] pem) throws GeneralSecurityException, IllegalArgumentException, NotPemException {
        String s;
        try {
            s = new String(pem, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new NotPemException("Invalid UTF-8");
        }
        return loadPem(s);
    }

    /**
     * Load the given PEM file.
     *
     * @param pem The PEM
     * @return A list of {@link java.security.cert.X509Certificate} and
     * {@link java.security.PrivateKey} instances
     * @throws GeneralSecurityException On JDK parsing failure
     * @throws IllegalArgumentException On MN parsing failure
     * @throws NotPemException On MN parsing failure that points to the input likely not being PEM
     * at all
     */
    List<Object> loadPem(String pem) throws GeneralSecurityException, IllegalArgumentException, NotPemException {
        // PEM is a sequence of base64 encoded DER objects delimited by -----BEGIN/END lines
        List<Object> list = new ArrayList<>();
        int i = 0;
        while (i < pem.length()) {
            if (Character.isWhitespace(pem.charAt(i))) {
                i++;
                continue;
            } else if (!pem.startsWith(START, i)) {
                if (list.isEmpty()) {
                    throw new NotPemException("Missing start tag");
                } else {
                    throw invalidPem(false);
                }
            }
            i += START.length();
            int labelEnd = pem.indexOf(DASHES, i);
            if (labelEnd == -1) {
                throw invalidPem(list.isEmpty());
            }
            String label = pem.substring(i, labelEnd);
            i = labelEnd + DASHES.length();
            String trailer = END + label + DASHES;
            int sectionEnd = pem.indexOf(trailer, i);
            if (sectionEnd == -1) {
                throw invalidPem(list.isEmpty());
            }
            Decoder decoder = getDecoder(label);

            String contentString = pem.substring(i, sectionEnd)
                .replace("\r", "")
                .replace("\n", "");
            i = sectionEnd + trailer.length();
            byte[] content = Base64.getDecoder().decode(contentString);
            list.addAll(decoder.decode(content));
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("PEM file empty");
        }
        return list;
    }

    private static IllegalArgumentException invalidPem(boolean first) throws NotPemException {
        if (first) {
            throw new NotPemException("Invalid PEM");
        }
        return new IllegalArgumentException("Invalid PEM");
    }

    private Decoder getDecoder(String label) {
        return switch (label) {
            case "CERTIFICATE", "X509 CERTIFICATE" -> new CertificateDecoder();
            case "ENCRYPTED PRIVATE KEY" -> new Pkcs8EncryptedPrivateKey();
            case "PRIVATE KEY" -> new Pkcs8PrivateKey();
            case "RSA PRIVATE KEY" -> new Pkcs1PrivateKey(false);
            case "EC PRIVATE KEY" -> new Pkcs1PrivateKey(true);
            default -> throw new IllegalArgumentException("Unsupported PEM label: " + label);
        };
    }

    private sealed interface Decoder {
        Collection<?> decode(byte[] der) throws GeneralSecurityException;
    }

    /**
     * X.509 certificate decoder.
     */
    private final class CertificateDecoder implements Decoder {
        @Override
        public Collection<?> decode(byte[] der) throws GeneralSecurityException {
            CertificateFactory factory = provider == null ? CertificateFactory.getInstance("X.509") : CertificateFactory.getInstance("X.509", provider);
            return factory.generateCertificates(new ByteArrayInputStream(der));
        }
    }

    /**
     * PKCS#8 unencrypted private key decoder with algorithm detection.
     */
    private final class Pkcs8PrivateKey implements Decoder {
        @Override
        public Collection<?> decode(byte[] der) throws GeneralSecurityException {
            // we need to figure out which key algorithm is used, so we parse the DER a bit.
            DerInput outer = new DerInput(der);
            DerInput privateKeyInfo = outer.readSequence();
            // Version
            privateKeyInfo.expect(0x02);
            privateKeyInfo.expect(0x01);
            privateKeyInfo.expect(0x00);
            DerInput privateKeyAlgorithm = privateKeyInfo.readSequence();
            String algOid = privateKeyAlgorithm.readOid();
            String alg = switch (algOid) {
                case OID_RSA -> "RSA";
                case OID_EC -> "EC";
                case "1.3.101.112" -> "Ed25519";
                case "1.3.101.113" -> "Ed448";
                case "2.16.840.1.101.3.4.3.17", "2.16.840.1.101.3.4.3.18",
                     "2.16.840.1.101.3.4.3.19" -> "ML-DSA";
                case "2.16.840.1.101.3.4.4.1", "2.16.840.1.101.3.4.4.2", "2.16.840.1.101.3.4.4.3" ->
                    "ML-KEM";
                default ->
                    throw new IllegalArgumentException("Unrecognized PKCS#8 key algorithm " + algOid);
            };

            KeyFactory factory = provider == null ? KeyFactory.getInstance(alg) : KeyFactory.getInstance(alg, provider);
            return List.of(factory.generatePrivate(new PKCS8EncodedKeySpec(der)));
        }
    }

    private final class Pkcs8EncryptedPrivateKey implements Decoder {
        @Override
        public Collection<?> decode(byte[] der) throws GeneralSecurityException {
            EncryptedPrivateKeyInfo keyInfo;
            try {
                keyInfo = new EncryptedPrivateKeyInfo(der);
            } catch (IOException e) {
                throw new GeneralSecurityException("Invalid DER", e);
            }
            String cipherAlg = keyInfo.getAlgName();
            if (cipherAlg.equals("PBES2")) {
                // Java >= 19 does this automatically
                cipherAlg = keyInfo.getAlgParameters().toString();
            }
            SecretKeyFactory skf = provider == null ? SecretKeyFactory.getInstance(cipherAlg) : SecretKeyFactory.getInstance(cipherAlg, provider);
            if (password == null) {
                throw new IllegalArgumentException("Encrypted private key found but no password given");
            }
            SecretKey sk = skf.generateSecret(new PBEKeySpec(password.toCharArray()));
            Cipher cipher = Cipher.getInstance(cipherAlg);
            cipher.init(Cipher.DECRYPT_MODE, sk, keyInfo.getAlgParameters());
            PKCS8EncodedKeySpec keySpec = keyInfo.getKeySpec(cipher);
            String keyAlg = keySpec.getAlgorithm();
            KeyFactory factory = provider == null ? KeyFactory.getInstance(keyAlg) : KeyFactory.getInstance(keyAlg, provider);
            return List.of(factory.generatePrivate(keySpec));
        }
    }

    private final class Pkcs1PrivateKey implements Decoder {
        // for ellptic curves, this is specified in SEC.1, not PKCS#1
        private final boolean ec;

        Pkcs1PrivateKey(boolean ec) {
            this.ec = ec;
        }

        @Override
        public Collection<?> decode(byte[] der) throws GeneralSecurityException {
            // build PKCS#8 from PKCS#1
            DerOutput output = new DerOutput();
            try (DerOutput.Value privateKeyInfo = output.writeValue(0x30)) {
                try (DerOutput.Value version = output.writeValue(0x2)) {
                    output.write(0);
                }
                try (DerOutput.Value privateKeyAlgorithm = output.writeValue(0x30)) {
                    if (ec) {
                        DerInput parameters = extractCurveParams(der);
                        output.writeOid(OID_EC);
                        output.write(parameters.data, parameters.i, parameters.limit - parameters.i);
                    } else {
                        output.writeOid(OID_RSA);
                        output.writeValue(0x05).close(); // parameters
                    }
                }
                try (DerOutput.Value privateKey = output.writeValue(0x04)) {
                    output.write(der, 0, der.length);
                }
            }
            byte[] pkcs8 = output.finish();
            String algorithm = ec ? "EC" : "RSA";
            KeyFactory factory = provider == null ? KeyFactory.getInstance(algorithm) : KeyFactory.getInstance(algorithm, provider);
            return List.of(factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8)));
        }

        private DerInput extractCurveParams(byte[] der) {
            DerInput input = new DerInput(der);
            DerInput ecPrivateKey = input.readSequence();
            // version
            ecPrivateKey.expect(0x02);
            ecPrivateKey.expect(0x01);
            ecPrivateKey.expect(0x01);
            ecPrivateKey.readValue(0x04); // privateKey
            DerInput parameters = null;
            while (ecPrivateKey.i < ecPrivateKey.limit) {
                int tag = ecPrivateKey.peekTag();
                DerInput value = ecPrivateKey.readValue(tag);
                if (tag == 0xa0) {
                    parameters = value;
                }
            }
            if (parameters == null) {
                throw new IllegalArgumentException("Curve parameters not found for EC private key");
            }
            return parameters;
        }
    }

    /**
     * Simple DER parser.
     */
    private static final class DerInput {
        final byte[] data;
        final int limit;
        int i;

        DerInput(byte[] data) {
            this(data, 0, data.length);
        }

        private DerInput(byte[] data, int start, int limit) {
            this.data = data;
            this.i = start;
            this.limit = limit;
        }

        /**
         * Read a single byte.
         *
         * @return The byte value
         */
        byte read() {
            if (i >= limit) {
                throw invalidDer();
            }
            return data[i++];
        }

        /**
         * Read a single byte, throwing an exception if it does not match the given value.
         *
         * @param value The expected byte
         */
        void expect(int value) {
            if ((read() & 0xff) != value) {
                throw invalidDer();
            }
        }

        /**
         * Read a DER tag length.
         *
         * @return The length
         */
        private int readLength() {
            byte b = read();
            if (b >= 0) {
                return b;
            }
            b &= 0x7f;
            // this is not as strict as it should be for DER, so don't use this for data where
            // parsing differentials could be a problem
            int length = 0;
            while (b-- > 0) {
                length <<= 8;
                length |= read() & 0xff;
                if (length < 0 || length > limit - i) {
                    throw invalidDer();
                }
            }
            return length;
        }

        /**
         * Get the type of the next tag.
         *
         * @return The next tag
         */
        int peekTag() {
            int tag = read() & 0xff;
            i--;
            return tag;
        }

        /**
         * Read a DER value. This {@link DerInput} will continue after the value, while the
         * returned {@link DerInput} will read the value contents.
         *
         * @param tag The expected tag
         * @return The reader for the value contents
         */
        DerInput readValue(int tag) {
            expect(tag);
            int n = readLength();
            int end = i + n;
            DerInput sequence = new DerInput(data, i, end);
            i = end;
            return sequence;
        }

        /**
         * Read a DER sequence.
         *
         * @return The reader for the sequence content
         */
        DerInput readSequence() {
            return readValue(0x30);
        }

        String readOid() {
            DerInput helper = readValue(0x06);
            StringBuilder builder = new StringBuilder();
            while (helper.i < helper.limit) {
                long value = 0;
                while (true) {
                    byte b = helper.read();
                    value <<= 7;
                    value |= b & 0x7f;
                    if (b >= 0) {
                        break;
                    }
                }
                if (builder.isEmpty()) {
                    // first value
                    if (value >= 80) {
                        builder.append("2.").append(value - 80);
                    } else {
                        builder.append(value / 40).append('.').append(value % 40);
                    }
                } else {
                    builder.append('.').append(value);
                }
            }
            return builder.toString();
        }

        private static RuntimeException invalidDer() {
            return new IllegalArgumentException("Invalid PKCS#8");
        }
    }

    /**
     * Writer for DER documents.
     */
    private static final class DerOutput {
        private byte[] out = ArrayUtils.EMPTY_BYTE_ARRAY;
        private int i;

        private void ensureCapacity(int n) {
            while (i + n > out.length) {
                out = Arrays.copyOf(out, out.length == 0 ? 16 : out.length * 2);
            }
        }

        /**
         * Write a single byte.
         *
         * @param b The byte
         */
        void write(int b) {
            ensureCapacity(1);
            out[i++] = (byte) b;
        }

        /**
         * Write a byte array.
         *
         * @param arr The array to write from
         * @param start The starting offset in the array
         * @param len The number of bytes to write
         */
        void write(byte[] arr, int start, int len) {
            ensureCapacity(len);
            System.arraycopy(arr, start, out, i, len);
            i += len;
        }

        /**
         * Finish this DER writer, returning the complete DER document as a byte array.
         *
         * @return The finished DER
         */
        byte[] finish() {
            return Arrays.copyOf(out, i);
        }

        private static int varIntLength(int value) {
            if (value < (1 << 7)) {
                return 1;
            } else if (value < (1 << 14)) {
                return 2;
            } else if (value < (1 << 21)) {
                return 3;
            } else if (value < (1 << 28)) {
                return 4;
            } else {
                return 5;
            }
        }

        private void writeVarInt(int value) {
            // writes a value in the bit form `1xxxxxxx 1xxxxxxx 0xxxxxxx`
            int len = varIntLength(value);
            for (int i = len - 1; i >= 0; i--) {
                write(((value >> (i * 7)) & 0x7f) | (i == 0 ? 0 : 0x80));
            }
        }

        void writeOid(String oid) {
            try (Value ignored = writeValue(0x06)) {
                String[] parts = oid.split("\\.");
                for (int j = 0; j < parts.length; j++) {
                    int value = Integer.parseInt(parts[j]);
                    if (j == 0) {
                        int next = Integer.parseInt(parts[++j]);
                        writeVarInt(value * 40 + next);
                    } else {
                        writeVarInt(value);
                    }
                }
            }

        }

        /**
         * Write a new DER value. The returned {@link Value} must be {@link Value#close() closed}
         * when the value is complete, at which point the value length will be written.
         *
         * @param tag The value tag
         * @return The value that must be closed to write the length
         */
        Value writeValue(int tag) {
            write(tag);
            return new Value();
        }

        final class Value implements AutoCloseable {
            private final int lengthOffset;

            private Value() {
                lengthOffset = i;
                write(0);
            }

            @Override
            public void close() {
                int length = i - lengthOffset - 1;
                int lengthLength;
                if (length < 0x80) {
                    out[lengthOffset] = (byte) length;
                    return;
                } else if (length < (1 << 8)) {
                    lengthLength = 1;
                } else if (length < (1 << 16)) {
                    lengthLength = 2;
                } else if (length < (1 << 24)) {
                    lengthLength = 3;
                } else {
                    lengthLength = 4;
                }
                out[lengthOffset] = (byte) (0x80 | lengthLength);
                // make sure we have room, and update position
                for (int i = 0; i < lengthLength; i++) {
                    write(0);
                }
                System.arraycopy(out, lengthOffset + 1, out, lengthOffset + 1 + lengthLength, length);
                int mark = i;
                i = lengthOffset + 1;
                for (int i = lengthLength - 1; i >= 0; i--) {
                    write((length >> (i * 8)) & 0xff);
                }
                i = mark;
            }
        }
    }

    /**
     * Thrown when the input does not look like PEM.
     */
    public static final class NotPemException extends Exception {
        private NotPemException(String message) {
            super(message);
        }
    }
}
