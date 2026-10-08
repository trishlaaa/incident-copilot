package com.trishla.copilot.triage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Turns a raw error message into a stable template, so that the same underlying
 * problem produces the same fingerprint even when IDs, numbers and timings differ.
 */
public final class Fingerprinter {

    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b");
    // hex-ish ids such as 3f2a9c1b: 6+ hex chars containing a digit, or any 8+ char hex token
    private static final Pattern HEX_ID = Pattern.compile(
            "\\b(?=[0-9a-fA-F]*\\d)[0-9a-fA-F]{6,}\\b|\\b[0-9a-fA-F]{8,}\\b");
    // Ids and numbers share one placeholder so "3f2a9c1b" and "12345678" give the same template.
    private static final String VAR = "<*>";
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Fingerprinter() { }

    public static String template(String message) {
        if (message == null) {
            return "";
        }
        String t = UUID.matcher(message).replaceAll("<uuid>");
        t = IPV4.matcher(t).replaceAll("<ip>");
        t = HEX_ID.matcher(t).replaceAll(VAR);
        t = NUMBER.matcher(t).replaceAll(VAR);
        return SPACES.matcher(t).replaceAll(" ").trim();
    }

    public static String fingerprint(String service, String exceptionType, String message) {
        String key = nz(service) + "|" + nz(exceptionType) + "|" + template(message);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
