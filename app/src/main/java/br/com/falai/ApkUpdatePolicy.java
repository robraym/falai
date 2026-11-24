package br.com.falai;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/** Integrity rules shared by download verification and local JVM tests. */
final class ApkUpdatePolicy {
    static final long MAX_BYTES = 200L * 1024 * 1024;
    enum Reason { FILE, PACKAGE, VERSION, SIGNATURE, DIGEST, SPACE }
    static final class Failure extends IOException {
        final Reason reason;
        Failure(Reason reason) { super(reason.name()); this.reason = reason; }
    }
    static String normalizeDigest(String digest) {
        if (digest == null || digest.isEmpty()) return "";
        if (!digest.matches("sha256:[a-fA-F0-9]{64}")) throw new IllegalArgumentException("Invalid asset digest");
        return digest.substring(7).toLowerCase(java.util.Locale.ROOT);
    }
    static void verifyFile(File file, long expectedSize, String digest) throws IOException {
        if (expectedSize <= 0 || expectedSize > MAX_BYTES || !file.isFile() || file.length() != expectedSize) throw new Failure(Reason.FILE);
        if (!digest.isEmpty()) {
            MessageDigest hash = sha256();
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);
            }
            if (!hex(hash.digest()).equals(digest)) throw new Failure(Reason.DIGEST);
        }
    }
    static void verifyIdentity(String installedPackage, long installedVersion, String candidatePackage, long candidateVersion,
            long expectedVersion, Set<String> installedSigners, Set<String> candidateSigners, Set<String> candidateHistory) throws Failure {
        if (!installedPackage.equals(candidatePackage)) throw new Failure(Reason.PACKAGE);
        if (candidateVersion != expectedVersion || candidateVersion <= installedVersion) throw new Failure(Reason.VERSION);
        if (installedSigners.isEmpty() || candidateSigners.isEmpty()) throw new Failure(Reason.SIGNATURE);
        boolean trusted = installedSigners.size() > 1 || candidateSigners.size() > 1
            ? installedSigners.equals(candidateSigners)
            : candidateHistory.containsAll(installedSigners) && candidateHistory.containsAll(candidateSigners);
        if (!trusted) throw new Failure(Reason.SIGNATURE);
    }
    static String certificate(byte[] bytes) { return hex(sha256().digest(bytes)); }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2], alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) { result[i * 2] = alphabet[(bytes[i] & 255) >>> 4]; result[i * 2 + 1] = alphabet[bytes[i] & 15]; }
        return new String(result);
    }
    private ApkUpdatePolicy() { }
}
