// Standalone Java 17 program that generates a self-signed EC P-256 accessory
// identity in the format LocalMfiAuthenticationClient.load() expects:
//   offline-mfi/identity.pk8   - PKCS8 DER-encoded EC private key
//   offline-mfi/certificate.p7b - DER-encoded X.509 v3 certificate
//
// This produces a "synthetic" identity: it passes the app's internal sign+verify
// self-check, so DiPlayBootstrap.ensure() and CarPlayController.startMfi() will
// accept it. It will NOT be trusted by iPhone itself, since Apple's MFi license
// is required for the real identity. Use a real identity for production pairing.
//
// Run:
//   java scripts/GenMfiIdentity.java <output-directory>
//
// Uses only JDK standard library (no BouncyCastle needed for generation).

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Date;

public class GenMfiIdentity {

    public static void main(String[] args) throws Exception {
        Path outDir = args.length > 0
                ? Path.of(args[0])
                : Path.of("offline-mfi");
        Path inner = outDir.resolve("offline-mfi");
        Files.createDirectories(inner);
        System.out.println("Generating synthetic MFi identity to: " + inner);

        // EC P-256 keypair. JDK built-in SunEC provider handles this.
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = g.generateKeyPair();
        PrivateKey priv = pair.getPrivate();

        byte[] pkcs8Key = priv.getEncoded();
        byte[] subjectPublicKeyInfo = pair.getPublic().getEncoded(); // DER SubjectPublicKeyInfo

        // ---- Build X.509 v3 cert (self-signed) ----
        Date notBefore = new Date(0L);
        Date notAfter = new Date(4102444800000L); // 2099-12-31
        int serial = 1;

        byte[] tbsCert = buildTbsCert(subjectPublicKeyInfo, notBefore, notAfter, serial);

        // Sign TBS with SHA256withECDSA using the same key.
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(priv);
        signer.update(tbsCert);
        byte[] sigDer = signer.sign();

        // Assemble Certificate ::= SEQUENCE { tbs, algorithmIdentifier, signatureValue BIT STRING }
        byte[] certDer = derSequence(
                tbsCert,
                derEcdsaSha256AlgorithmId(),
                derBitString(sigDer)
        );

        Files.write(inner.resolve("identity.pk8"), pkcs8Key);
        Files.write(inner.resolve("certificate.p7b"), certDer);

        // Verify: load the cert back and check public key matches keypair.
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new java.io.ByteArrayInputStream(certDer));
        var certPubKey = (java.security.PublicKey) cert.getPublicKey();
        var keyPubKey = pair.getPublic();
        boolean pubKeyMatch = certPubKey.getAlgorithm().equals(keyPubKey.getAlgorithm())
                && Arrays.equals(certPubKey.getEncoded(), keyPubKey.getEncoded());
        if (!pubKeyMatch) {
            System.err.println("cert pub algorithm=" + certPubKey.getAlgorithm()
                    + " encodedLen=" + certPubKey.getEncoded().length);
            System.err.println("key  pub algorithm=" + keyPubKey.getAlgorithm()
                    + " encodedLen=" + keyPubKey.getEncoded().length);
            throw new IllegalStateException("Public key mismatch after generation");
        }

        // Verify: sign+verify round-trip with the loaded key and cert.
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(certPubKey);
        byte[] challenge = java.util.Arrays.copyOfRange(sigDer, 0, 32);
        Signature sigCheck = Signature.getInstance("SHA256withECDSA");
        sigCheck.initSign(priv);
        sigCheck.update(challenge);
        byte[] challengeSig = sigCheck.sign();
        verifier.update(challenge);
        if (!verifier.verify(challengeSig)) {
            throw new IllegalStateException("Sign/verify round-trip failed");
        }

        System.out.println("  identity.pk8   " + pkcs8Key.length + " bytes");
        System.out.println("  certificate.p7b " + certDer.length + " bytes");
        System.out.println("  serial=" + serial
                + " subject=" + cert.getSubjectX500Principal()
                + " notAfter=" + cert.getNotAfter());
        System.out.println("DONE. NOTE: synthetic identity — Apple will not trust it. Use a real MFi identity for production.");
    }

    // ---- ASN.1 / DER helpers ----

    static byte[] derSequence(byte[]... elements) {
        return der(0x30, concat(elements));
    }

    static byte[] derSet(byte[]... elements) {
        return der(0x31, concat(elements));
    }

    static byte[] derInteger(BigInteger value) {
        byte[] raw = value.toByteArray();
        // Strip leading zero padding where possible but keep sign bit.
        int start = 0;
        while (start < raw.length - 1 && raw[start] == 0 && (raw[start + 1] & 0x80) == 0) {
            start++;
        }
        return der(0x02, Arrays.copyOfRange(raw, start, raw.length));
    }

    static byte[] derUtcTime(Date d) {
        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(d);
        String s = String.format(
                "%02d%02d%02d%02d%02d%02dZ",
                cal.get(java.util.Calendar.YEAR) % 100,
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND));
        return der(0x17, s.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] derBitString(byte[] payload) {
        byte[] content = new byte[payload.length + 1];
        content[0] = 0; // unused bits
        System.arraycopy(payload, 0, content, 1, payload.length);
        return der(0x03, content);
    }

    static byte[] derOctetString(byte[] payload) {
        return der(0x04, payload);
    }

    static byte[] derNull() {
        return new byte[] {0x05, 0x00};
    }

    static byte[] derObjectIdentifier(String dotted) {
        byte[] enc = encodeOid(dotted);
        return der(0x06, enc);
    }

    static byte[] derUtf8String(String s) {
        return der(0x0c, s.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] der(int tagAndClass, byte[] content) {
        return der(true, tagAndClass, content);
    }

    static byte[] der(boolean constructed, int tagAndClass, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tagAndClass);
        int len = content.length;
        if (len < 0x80) {
            out.write(len);
        } else {
            int numBytes = 0;
            for (int v = len; v > 0; v >>>= 8) numBytes++;
            out.write(0x80 | numBytes);
            for (int i = numBytes - 1; i >= 0; i--) {
                out.write((len >>> (8 * i)) & 0xff);
            }
        }
        out.write(content, 0, content.length);
        return out.toByteArray();
    }

    static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    static byte[] encodeOid(String dotted) {
        String[] parts = dotted.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            long v = Long.parseLong(parts[i]);
            if (v == 0) {
                out.write(0);
            } else {
                byte[] bytes = new byte[(int) (Long.SIZE - Long.numberOfLeadingZeros(v) + 7) / 8];
                int pos = bytes.length;
                long tmp = v;
                while (tmp > 0) {
                    bytes[--pos] = (byte) ((tmp & 0x7f) | 0x80);
                    tmp >>>= 7;
                }
                bytes[bytes.length - 1] &= 0x7f;
                out.write(bytes, 0, bytes.length);
            }
        }
        return out.toByteArray();
    }

    static byte[] derEcdsaSha256AlgorithmId() {
        return derSequence(
                derObjectIdentifier("1.2.840.10045.4.3.2"), // ecdsa-with-SHA256
                derObjectIdentifier("1.2.840.10045.3.1.7")  // prime256v1
        );
    }

    static byte[] derEcPublicKeyAlgorithmId() {
        // AlgorithmIdentifier { ecPublicKey, prime256v1 } — no parameters, encoded as NULL
        return derSequence(
                derObjectIdentifier("1.2.840.10045.2.1"),
                derObjectIdentifier("1.2.840.10045.3.1.7")
        );
    }

    static byte[] buildTbsCert(byte[] subjectPublicKeyInfoDer, Date notBefore, Date notAfter, int serial) {
        byte[] subjectPublicKeyInfo = subjectPublicKeyInfoDer;

        // X500Name "CN=DiPlay synthetic offline-mfi identity"
        byte[] rdnSequence = derSequence(
                derSet(
                        derSequence(
                                derObjectIdentifier("2.5.4.3"), // id-at-commonName
                                derUtf8String("DiPlay synthetic offline-mfi identity")
                        )
                )
        );

        // Validity ::= SEQUENCE { notBefore, notAfter }
        byte[] validity = derSequence(
                derUtcTime(notBefore),
                derUtcTime(notAfter)
        );

        byte[] tbs = derSequence(
                derInteger(BigInteger.valueOf(serial)),
                derEcdsaSha256AlgorithmId(),
                rdnSequence,
                validity,
                rdnSequence, // issuer == subject (self-signed)
                subjectPublicKeyInfo
        );

        return tbs;
    }
}
