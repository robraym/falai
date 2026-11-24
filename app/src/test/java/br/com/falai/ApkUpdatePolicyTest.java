package br.com.falai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public final class ApkUpdatePolicyTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final String HELLO_HASH = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    private static Set<String> certs(String... values) { return new HashSet<>(Arrays.asList(values)); }
    private File file(String content) throws Exception {
        File file = temporary.newFile();
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(content.getBytes(StandardCharsets.UTF_8)); }
        return file;
    }
    @Test public void acceptsExactSizeAndPublishedHash() throws Exception {
        ApkUpdatePolicy.verifyFile(file("hello"), 5, ApkUpdatePolicy.normalizeDigest("sha256:" + HELLO_HASH.toUpperCase(java.util.Locale.ROOT)));
    }
    @Test public void rejectsTruncatedDownloadEvenWithoutPublishedHash() throws Exception {
        assertEquals(ApkUpdatePolicy.Reason.FILE, assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyFile(file("hell"), 5, "")).reason);
    }
    @Test public void rejectsSameSizeCorruption() throws Exception {
        assertEquals(ApkUpdatePolicy.Reason.DIGEST, assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyFile(file("jello"), 5, HELLO_HASH)).reason);
    }
    @Test public void legacyReleasesMayOmitDigestButMalformedDigestIsNotIgnored() {
        assertEquals("", ApkUpdatePolicy.normalizeDigest(null));
        assertEquals("", ApkUpdatePolicy.normalizeDigest(""));
        assertThrows(IllegalArgumentException.class, () -> ApkUpdatePolicy.normalizeDigest("sha256:1234"));
    }
    @Test public void packageMustMatchInstalledApp() {
        assertEquals(ApkUpdatePolicy.Reason.PACKAGE, assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyIdentity("br.com.falai", 100, "br.com.other", 101, 101, certs("key"), certs("key"), certs("key"))).reason);
    }
    @Test public void archiveMustBeTheOfferedVersionAndNewerThanInstalled() {
        assertEquals(ApkUpdatePolicy.Reason.VERSION, assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyIdentity("falai", 100, "falai", 102, 101, certs("key"), certs("key"), certs("key"))).reason);
        assertEquals(ApkUpdatePolicy.Reason.VERSION, assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyIdentity("falai", 100, "falai", 100, 100, certs("key"), certs("key"), certs("key"))).reason);
    }
    @Test public void unrelatedOrMissingSigningCertificateIsRejected() {
        assertEquals(ApkUpdatePolicy.Reason.SIGNATURE, assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyIdentity("falai", 100, "falai", 101, 101, certs("old"), certs("other"), certs("other"))).reason);
        assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyIdentity("falai", 100, "falai", 101, 101, certs("old"), certs(), certs()));
    }
    @Test public void acceptsProvenForwardCertificateRotationAndRejectsRollback() throws Exception {
        ApkUpdatePolicy.verifyIdentity("falai", 100, "falai", 101, 101, certs("old"), certs("new"), certs("old", "new"));
        assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyIdentity("falai", 100, "falai", 101, 101, certs("new"), certs("old"), certs("old")));
    }
    @Test public void multiSignerPackagesRequireTheExactSignerSet() throws Exception {
        ApkUpdatePolicy.verifyIdentity("falai", 100, "falai", 101, 101, certs("a", "b"), certs("b", "a"), certs());
        assertThrows(ApkUpdatePolicy.Failure.class,
            () -> ApkUpdatePolicy.verifyIdentity("falai", 100, "falai", 101, 101, certs("a", "b"), certs("a"), certs("a", "b")));
    }
}
