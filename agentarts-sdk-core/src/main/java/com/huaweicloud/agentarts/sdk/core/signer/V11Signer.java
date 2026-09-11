package com.huaweicloud.agentarts.sdk.core.signer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * V11-HMAC-SHA256 request signer for AgentArts self-developed endpoints.
 *
 * <p>V11-HMAC-SHA256 request signer for AgentArts endpoints.
 * Used for signing HTTP requests to AgentArts control plane, data plane,
 * runtime invoke, code interpreter, and gateway endpoints.</p>
 *
 * <h2>Algorithm overview:</h2>
 * <ol>
 *   <li>Generate timestamp, set {@code x-sdk-date} header</li>
 *   <li>Build Canonical Request from method, URI, query, headers</li>
 *   <li>Derive signing key via HKDF (AK as salt, SK as IKM)</li>
 *   <li>Compute HMAC-SHA256 signature over StringToSign</li>
 *   <li>Build Authorization header</li>
 * </ol>
 *
 * <h2>Algorithm notes:</h2>
 * <ul>
 *   <li>HKDF uses AK as salt and SK as IKM (reversed from typical HKDF conventions)</li>
 *   <li>Canonical URI always ends with trailing {@code /}</li>
 *   <li>Payload hash is always {@code UNSIGNED-PAYLOAD}</li>
 *   <li>Derived key is returned as hex string, then UTF-8 encoded to bytes for HMAC key (double encoding)</li>
 * </ul>
 */
public class V11Signer {

    static final String ALGORITHM = "V11-HMAC-SHA256";
    static final String APIC = "apic";
    static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    static final String HEADER_X_SDK_DATE = "x-sdk-date";
    static final String HEADER_X_SDK_CONTENT_SHA256 = "x-sdk-content-sha256";
    static final String HEADER_AUTHORIZATION = "Authorization";
    static final String HEADER_X_SECURITY_TOKEN = "X-Security-Token";
    static final String HEADER_HOST = "host";

    static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String ak;
    private final String sk;
    private final String regionId;

    /**
     * Create a new V11Signer.
     *
     * @param ak       Huawei Cloud Access Key ID
     * @param sk       Huawei Cloud Secret Access Key
     * @param regionId Huawei Cloud region (e.g., "cn-southwest-2")
     */
    public V11Signer(String ak, String sk, String regionId) {
        if (ak == null || ak.isEmpty()) {
            throw new IllegalArgumentException("Access Key (ak) must not be null or empty");
        }
        if (sk == null || sk.isEmpty()) {
            throw new IllegalArgumentException("Secret Key (sk) must not be null or empty");
        }
        if (regionId == null || regionId.isEmpty()) {
            throw new IllegalArgumentException("Region ID must not be null or empty");
        }
        this.ak = ak;
        this.sk = sk;
        this.regionId = regionId;
    }

    /**
     * Sign an HTTP request by adding {@code x-sdk-date} and {@code Authorization} headers.
     *
     * @param method      HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param path        URL path (e.g., "/v1/runtimes")
     * @param queryParams query parameters (nullable)
     * @param headers     mutable map of HTTP headers — will be modified in-place
     * @return the same headers map with signing headers added
     */
    public Map<String, String> sign(String method, String path,
                                     Map<String, List<String>> queryParams,
                                     Map<String, String> headers) {
        if (method == null || method.isEmpty()) {
            throw new IllegalArgumentException("HTTP method must not be null or empty");
        }
        if (path == null) {
            throw new IllegalArgumentException("URL path must not be null");
        }
        if (headers == null) {
            throw new IllegalArgumentException("Headers map must not be null");
        }

        // 1. Generate timestamp (only if not already set — allows test injection)
        String timestamp = headers.get(HEADER_X_SDK_DATE);
        if (timestamp == null || timestamp.isEmpty()) {
            timestamp = getTimestamp();
            headers.put(HEADER_X_SDK_DATE, timestamp);
        }

        // 2. Compute signed headers
        List<String> signedHeaders = getSignedHeaders(headers);

        // 3. Build canonical request
        String canonicalRequest = buildCanonicalRequest(method, path, new HashMap<>(), headers, signedHeaders);

        // 4. Build string to sign
        String credentialScope = buildCredentialScope(timestamp);
        String stringToSign = buildStringToSign(canonicalRequest, timestamp, credentialScope);

        // 5. Derive signing key via HKDF
        String realUseSecret = getRealUseSecret(credentialScope);

        // 6. Compute signature
        String signature = signStringToSign(realUseSecret, stringToSign);

        // 7. Build and set Authorization header
        String authHeader = buildAuthHeaderValue(credentialScope, signedHeaders, signature);
        headers.put(HEADER_AUTHORIZATION, authHeader);

        return headers;
    }

    // ========================
    // Timestamp
    // ========================

    String getTimestamp() {
        return DATE_FORMAT.format(Instant.now());
    }

    // For testing: allow injecting a fixed timestamp
    String getTimestamp(Instant instant) {
        return DATE_FORMAT.format(instant);
    }

    // ========================
    // Credential Scope
    // ========================

    /**
     * Build credential scope: {date8}/{region}/apic
     * Example: 20250615/cn-southwest-2/apic
     */
    String buildCredentialScope(String timestamp) {
        return timestamp.substring(0, 8) + "/" + regionId + "/" + APIC;
    }

    // ========================
    // Canonical Request
    // ========================

    /**
     * Build canonical request from 6 parts joined by newlines:
     * METHOD\n
     * canonicalUri\n
     * canonicalQueryString\n
     * canonicalHeaders\n
     * signedHeaders;\n
     * UNSIGNED-PAYLOAD
     */
    String buildCanonicalRequest(String method, String path,
                                  Map<String, List<String>> queryParams,
                                  Map<String, String> headers,
                                  List<String> signedHeaders) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.toUpperCase()).append('\n');
        sb.append(canonicalUri(path)).append('\n');
        sb.append(canonicalQueryString(queryParams)).append('\n');
        sb.append(canonicalHeaders(headers, signedHeaders));
        sb.append(String.join(";", signedHeaders)).append('\n');
        sb.append(UNSIGNED_PAYLOAD);
        return sb.toString();
    }

    // ========================
    // Canonical URI
    // ========================

    /**
     * Encode path as canonical URI.
     *
     * <p>Steps:
     * <ol>
     *   <li>URL-decode the path</li>
     *   <li>Split by "/"</li>
     *   <li>Re-encode each segment with URLEncoder (safe "~")</li>
     *   <li>Join with "/"</li>
     *   <li>Force trailing "/"</li>
     * </ol>
     */
    String canonicalUri(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // URL-decode first
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);

        // Split by "/" and re-encode each segment
        String[] segments = decoded.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(urlEncode(segments[i]));
        }

        String result = sb.toString();

        // Ensure starts with /
        if (!result.startsWith("/")) {
            result = "/" + result;
        }

        // Force trailing /
        if (!result.endsWith("/")) {
            result = result + "/";
        }

        return result;
    }

    // ========================
    // Canonical Query String
    // ========================

    /**
     * Build canonical query string.
     *
     * <p>Keys sorted alphabetically. List values sorted and expanded to multiple pairs.
     * Both keys and values are URL-encoded (safe "~").</p>
     */
    String canonicalQueryString(Map<String, List<String>> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }

        TreeMap<String, List<String>> sorted = new TreeMap<>(queryParams);
        List<String> pairs = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : sorted.entrySet()) {
            String encodedKey = urlEncode(entry.getKey());
            List<String> values = entry.getValue();

            if (values == null || values.isEmpty()) {
                pairs.add(encodedKey + "=");
            } else if (values.size() == 1) {
                pairs.add(encodedKey + "=" + urlEncodeQueryValue(values.get(0)));
            } else {
                // Sort list values and produce multiple key=value pairs
                List<String> sortedValues = new ArrayList<>(values);
                Collections.sort(sortedValues);
                for (String value : sortedValues) {
                    pairs.add(encodedKey + "=" + urlEncodeQueryValue(value));
                }
            }
        }

        return String.join("&", pairs);
    }

    // ========================
    // Canonical Headers
    // ========================

    /**
     * Build canonical headers string.
     *
     * <p>Each header as "lowercase_key:trimmed_value\n", in signed_headers order.
     * Ends with an extra newline.</p>
     */
    String canonicalHeaders(Map<String, String> headers, List<String> signedHeaders) {
        // Build a lowercase-key map
        TreeMap<String, String> lowerMap = new TreeMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            lowerMap.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().trim());
        }

        StringBuilder sb = new StringBuilder();
        for (String key : signedHeaders) {
            String value = lowerMap.getOrDefault(key, "");
            sb.append(key).append(':').append(value).append('\n');
        }
        // Extra trailing newline (part of canonical headers format)
        sb.append('\n');
        return sb.toString();
    }

    // ========================
    // Signed Headers
    // ========================

    /**
     * Get sorted list of lowercase header names.
     */
    List<String> getSignedHeaders(Map<String, String> headers) {
        TreeSet<String> uniqueNames = new TreeSet<>();
        for (String key : headers.keySet()) {
            uniqueNames.add(key.toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(uniqueNames);
    }

    // ========================
    // String to Sign
    // ========================

    /**
     * Build string to sign:
     * V11-HMAC-SHA256\n
     * {timestamp}\n
     * {credentialScope}\n
     * {sha256(canonicalRequest).hex}
     */
    String buildStringToSign(String canonicalRequest, String timestamp, String credentialScope) {
        return ALGORITHM + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
    }

    // ========================
    // HKDF Key Derivation
    // ========================

    /**
     * Derive signing key via HKDF.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Extract: prk = HMAC-SHA256(key=AK, data=SK)</li>
     *   <li>Expand: t = HMAC-SHA256(key=prk, data=empty + info + 0x01)</li>
     *   <li>Return t as hex string (64 chars)</li>
     * </ol>
     *
     * <p>NOTE: AK is the salt and SK is the IKM — reversed from typical HKDF.</p>
     */
    String getRealUseSecret(String credentialScope) {
        return hkdf(ak, sk, credentialScope, 32);
    }

    /**
     * HKDF implementation using HMAC-SHA256.
     *
     * @param salt   the salt (in our case, the AK)
     * @param ikm    input keying material (in our case, the SK)
     * @param info   context info (credential scope)
     * @param length desired output length in bytes (always 32)
     * @return hex-encoded derived key
     */
    String hkdf(String salt, String ikm, String info, int length) {
        try {
            byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
            byte[] ikmBytes = ikm.getBytes(StandardCharsets.UTF_8);
            byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);

            // Extract phase: prk = HMAC-SHA256(key=salt, data=ikm)
            byte[] prk = hmacSha256(saltBytes, ikmBytes);

            // Expand phase
            byte[] okm = new byte[0];
            byte[] t = new byte[0];
            int iterations = (length + 31) / 32; // ceil(length / 32)

            for (int i = 1; i <= iterations; i++) {
                // new_info = t + info + byte(i)
                byte[] newInfo = new byte[t.length + infoBytes.length + 1];
                System.arraycopy(t, 0, newInfo, 0, t.length);
                System.arraycopy(infoBytes, 0, newInfo, t.length, infoBytes.length);
                newInfo[newInfo.length - 1] = (byte) i;

                t = hmacSha256(prk, newInfo);

                // Append t to okm
                byte[] newOkm = new byte[okm.length + t.length];
                System.arraycopy(okm, 0, newOkm, 0, okm.length);
                System.arraycopy(t, 0, newOkm, okm.length, t.length);
                okm = newOkm;
            }

            // Truncate to desired length and convert to hex
            byte[] truncated = Arrays.copyOf(okm, length);
            return bytesToHex(truncated);
        } catch (Exception e) {
            throw new RuntimeException("HKDF key derivation failed", e);
        }
    }

    // ========================
    // Signature Computation
    // ========================

    /**
     * Compute HMAC-SHA256 signature.
     *
     * <p>NOTE: The realUseSecret is a hex STRING (64 chars), which is then
     * converted to UTF-8 BYTES for use as the HMAC key. This is a double-encoding
     * pattern specific to V11 signing.</p>
     */
    String signStringToSign(String realUseSecret, String stringToSign) {
        try {
            // Hex string → UTF-8 bytes (double encoding!)
            byte[] keyBytes = realUseSecret.getBytes(StandardCharsets.UTF_8);
            byte[] dataBytes = stringToSign.getBytes(StandardCharsets.UTF_8);
            byte[] signature = hmacSha256(keyBytes, dataBytes);
            return bytesToHex(signature);
        } catch (Exception e) {
            throw new RuntimeException("Signature computation failed", e);
        }
    }

    // ========================
    // Authorization Header
    // ========================

    /**
     * Build Authorization header value:
     * V11-HMAC-SHA256 Credential={ak}/{credentialScope}, SignedHeaders={;join}, Signature={signature}
     */
    String buildAuthHeaderValue(String credentialScope, List<String> signedHeaders, String signature) {
        return ALGORITHM + " Credential=" + ak + "/" + credentialScope
                + ", SignedHeaders=" + String.join(";", signedHeaders)
                + ", Signature=" + signature;
    }

    // ========================
    // Crypto primitives
    // ========================

    /**
     * Compute HMAC-SHA256.
     */
    static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }

    /**
     * Compute SHA-256 hash and return as hex string.
     */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failed", e);
        }
    }

    // ========================
    // URL Encoding
    // ========================

    /**
     * URL-encode a string, keeping only "~" as safe character.
     * This matches Python's {@code urllib.parse.quote(s, safe="~")}.
     */
    static String urlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        // URLEncoder encodes space as "+" but we need "%20"
        // URLEncoder encodes "~" but we need it safe
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        // Fix: "+" → "%20" (URLEncoder uses + for spaces, but RFC 3986 requires %20)
        encoded = encoded.replace("+", "%20");
        // Fix: URLEncoder encodes "~" as "%7E", but we want "~" to be safe
        encoded = encoded.replace("%7E", "~");
        // Fix: URLEncoder encodes "*" as "*", but RFC 3986 requires "%2A"
        encoded = encoded.replace("*", "%2A");
        return encoded;
    }

    /**
     * URL-encode a query value, keeping "~" and "/" as safe characters.
     * This matches Python's {@code urllib.parse.quote(s, safe="~/")}: the
     * data-plane gateway decodes the wire query and treats "/" as safe when
     * re-canonicalising, so the signature must keep slashes unencoded (e.g.
     * an upload's {@code path} value like {@code /home/user/test.txt}).
     */
    static String urlEncodeQueryValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return urlEncode(value).replace("%2F", "/");
    }

    // ========================
    // Byte/Hex conversion
    // ========================

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0f];
        }
        return new String(hex);
    }

    // ========================
    // Factory method (matches Python create_v11_signer)
    // ========================

    /**
     * Create a new V11Signer instance.
     *
     * @param ak       Huawei Cloud Access Key ID
     * @param sk       Huawei Cloud Secret Access Key
     * @param regionId Huawei Cloud region
     * @return new V11Signer
     */
    public static V11Signer create(String ak, String sk, String regionId) {
        return new V11Signer(ak, sk, regionId);
    }
}
