package com.huaweicloud.agentarts.sdk.core.signer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link V11Signer}.
 *
 * <p>Tests are structured to verify each intermediate value independently,
 * enabling cross-validation with the Python signer_v11.py implementation.</p>
 */
class V11SignerTest {

    private static final String TEST_AK = "TESTAK1234567890AB";
    private static final String TEST_SK = "TESTSK1234567890ABCDEF1234567890";
    private static final String TEST_REGION = "cn-southwest-2";

    private final V11Signer signer = new V11Signer(TEST_AK, TEST_SK, TEST_REGION);

    // ========================
    // Constructor validation
    // ========================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        void shouldRejectNullAk() {
            assertThrows(IllegalArgumentException.class,
                    () -> new V11Signer(null, TEST_SK, TEST_REGION));
        }

        @Test
        void shouldRejectEmptyAk() {
            assertThrows(IllegalArgumentException.class,
                    () -> new V11Signer("", TEST_SK, TEST_REGION));
        }

        @Test
        void shouldRejectNullSk() {
            assertThrows(IllegalArgumentException.class,
                    () -> new V11Signer(TEST_AK, null, TEST_REGION));
        }

        @Test
        void shouldRejectNullRegion() {
            assertThrows(IllegalArgumentException.class,
                    () -> new V11Signer(TEST_AK, TEST_SK, null));
        }

        @Test
        void shouldCreateViaFactory() {
            V11Signer s = V11Signer.create(TEST_AK, TEST_SK, TEST_REGION);
            assertNotNull(s);
        }
    }

    // ========================
    // URL Encoding
    // ========================

    @Nested
    @DisplayName("URL Encoding")
    class UrlEncodingTests {

        @Test
        void shouldEncodeEmptyString() {
            assertEquals("", V11Signer.urlEncode(""));
        }

        @Test
        void shouldPreserveTilde() {
            assertEquals("hello~world", V11Signer.urlEncode("hello~world"));
        }

        @Test
        void shouldEncodeSpaces() {
            assertEquals("hello%20world", V11Signer.urlEncode("hello world"));
        }

        @Test
        void shouldEncodeSpecialChars() {
            String encoded = V11Signer.urlEncode("a=b&c=d");
            assertEquals("a%3Db%26c%3Dd", encoded);
        }

        @Test
        void shouldEncodeSlash() {
            assertEquals("a%2Fb", V11Signer.urlEncode("a/b"));
        }

        @Test
        void shouldEncodeStar() {
            assertEquals("%2A", V11Signer.urlEncode("*"));
        }

        @Test
        void shouldPreserveAlphanumeric() {
            assertEquals("abc123", V11Signer.urlEncode("abc123"));
        }

        @Test
        void shouldEncodeQueryValueKeepingSlash() {
            // Python: urllib.parse.quote("/home/user/test.txt", safe="~/")
            assertEquals("/home/user/test.txt", V11Signer.urlEncodeQueryValue("/home/user/test.txt"));
        }

        @Test
        void shouldEncodeQueryValueEncodingSpaceButNotSlash() {
            // Python: quote("a b/c", safe="~/") -> "a%20b/c"
            assertEquals("a%20b/c", V11Signer.urlEncodeQueryValue("a b/c"));
        }
    }

    // ========================
    // Canonical URI
    // ========================

    @Nested
    @DisplayName("Canonical URI")
    class CanonicalUriTests {

        @Test
        void shouldReturnSlashForNull() {
            assertEquals("/", signer.canonicalUri(null));
        }

        @Test
        void shouldReturnSlashForEmpty() {
            assertEquals("/", signer.canonicalUri(""));
        }

        @Test
        void shouldForceTrailingSlash() {
            assertEquals("/v1/runtimes/", signer.canonicalUri("/v1/runtimes"));
        }

        @Test
        void shouldKeepExistingTrailingSlash() {
            assertEquals("/v1/runtimes/", signer.canonicalUri("/v1/runtimes/"));
        }

        @Test
        void shouldEncodeSpecialCharsInPath() {
            // "my agent" → "my%20agent"
            assertEquals("/v1/my%20agent/", signer.canonicalUri("/v1/my agent"));
        }

        @Test
        void shouldDecodeAndReEncode() {
            // %20 decoded to space, then re-encoded to %20
            assertEquals("/v1/my%20agent/", signer.canonicalUri("/v1/my%20agent"));
        }

        @Test
        void shouldPreserveTildeInPath() {
            assertEquals("/v1/~user/", signer.canonicalUri("/v1/~user"));
        }

        @Test
        void shouldHandleRootPath() {
            assertEquals("/", signer.canonicalUri("/"));
        }
    }

    // ========================
    // Canonical Query String
    // ========================

    @Nested
    @DisplayName("Canonical Query String")
    class CanonicalQueryStringTests {

        @Test
        void shouldReturnEmptyForNull() {
            assertEquals("", signer.canonicalQueryString(null));
        }

        @Test
        void shouldReturnEmptyForEmptyMap() {
            assertEquals("", signer.canonicalQueryString(Map.of()));
        }

        @Test
        void shouldSortKeysAlphabetically() {
            Map<String, List<String>> params = new LinkedHashMap<>();
            params.put("zebra", List.of("1"));
            params.put("alpha", List.of("2"));

            String result = signer.canonicalQueryString(params);
            assertEquals("alpha=2&zebra=1", result);
        }

        @Test
        void shouldHandleListValues() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("tag", Arrays.asList("b", "a", "c"));

            String result = signer.canonicalQueryString(params);
            // List values should be sorted
            assertEquals("tag=a&tag=b&tag=c", result);
        }

        @Test
        void shouldEncodeKeysAndValues() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("my key", List.of("my value"));

            String result = signer.canonicalQueryString(params);
            assertEquals("my%20key=my%20value", result);
        }
    }

    // ========================
    // Signed Headers
    // ========================

    @Nested
    @DisplayName("Signed Headers")
    class SignedHeadersTests {

        @Test
        void shouldReturnSortedLowercaseHeaders() {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Host", "example.com");
            headers.put("X-Sdk-Date", "20250615T120000Z");
            headers.put("Content-Type", "application/json");

            List<String> result = signer.getSignedHeaders(headers);
            assertEquals(List.of("content-type", "host", "x-sdk-date"), result);
        }
    }

    // ========================
    // Canonical Headers
    // ========================

    @Nested
    @DisplayName("Canonical Headers")
    class CanonicalHeadersTests {

        @Test
        void shouldFormatHeadersCorrectly() {
            Map<String, String> headers = new HashMap<>();
            headers.put("host", "example.com");
            headers.put("x-sdk-date", "20250615T120000Z");

            List<String> signedHeaders = List.of("host", "x-sdk-date");

            String result = signer.canonicalHeaders(headers, signedHeaders);
            assertEquals("host:example.com\nx-sdk-date:20250615T120000Z\n\n", result);
        }

        @Test
        void shouldTrimHeaderValues() {
            Map<String, String> headers = new HashMap<>();
            headers.put("host", "  example.com  ");

            List<String> signedHeaders = List.of("host");

            String result = signer.canonicalHeaders(headers, signedHeaders);
            assertEquals("host:example.com\n\n", result);
        }
    }

    // ========================
    // Credential Scope
    // ========================

    @Nested
    @DisplayName("Credential Scope")
    class CredentialScopeTests {

        @Test
        void shouldBuildCorrectScope() {
            String scope = signer.buildCredentialScope("20250615T120000Z");
            assertEquals("20250615/cn-southwest-2/apic", scope);
        }
    }

    // ========================
    // HKDF
    // ========================

    @Nested
    @DisplayName("HKDF Key Derivation")
    class HkdfTests {

        @Test
        void shouldProduce64CharHexOutput() {
            String result = signer.hkdf("salt", "ikm", "info", 32);
            assertEquals(64, result.length(), "HKDF output should be 64 hex chars (32 bytes)");
            assertTrue(result.matches("[0-9a-f]{64}"), "Should be valid hex");
        }

        @Test
        void shouldBeDeterministic() {
            String r1 = signer.hkdf(TEST_AK, TEST_SK, "20250615/cn-southwest-2/apic", 32);
            String r2 = signer.hkdf(TEST_AK, TEST_SK, "20250615/cn-southwest-2/apic", 32);
            assertEquals(r1, r2);
        }

        @Test
        void shouldDifferForDifferentScopes() {
            String r1 = signer.hkdf(TEST_AK, TEST_SK, "20250615/cn-southwest-2/apic", 32);
            String r2 = signer.hkdf(TEST_AK, TEST_SK, "20250616/cn-southwest-2/apic", 32);
            assertNotEquals(r1, r2);
        }
    }

    // ========================
    // SHA-256
    // ========================

    @Nested
    @DisplayName("SHA-256")
    class Sha256Tests {

        @Test
        void shouldHashEmptyString() {
            // SHA-256 of empty string is well-known
            String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            assertEquals(expected, V11Signer.sha256Hex(""));
        }

        @Test
        void shouldHashHelloWorld() {
            String expected = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";
            assertEquals(expected, V11Signer.sha256Hex("Hello World"));
        }
    }

    // ========================
    // HMAC-SHA256
    // ========================

    @Nested
    @DisplayName("HMAC-SHA256")
    class HmacTests {

        @Test
        void shouldComputeHmac() {
            byte[] result = V11Signer.hmacSha256("key".getBytes(), "data".getBytes());
            assertNotNull(result);
            assertEquals(32, result.length);
        }
    }

    // ========================
    // Full Signing Flow
    // ========================

    @Nested
    @DisplayName("Full Signing Flow")
    class FullSignTests {

        @Test
        void shouldAddAuthorizationHeader() {
            Map<String, String> headers = new HashMap<>();
            headers.put("host", "agentarts.cn-southwest-2.myhuaweicloud.com");
            headers.put("Content-Type", "application/json");

            Map<String, String> result = signer.sign("POST", "/v1/runtimes", null, headers);

            assertTrue(result.containsKey("Authorization"));
            assertTrue(result.containsKey("x-sdk-date"));

            String auth = result.get("Authorization");
            assertTrue(auth.startsWith("V11-HMAC-SHA256 Credential="));
            assertTrue(auth.contains("SignedHeaders="));
            assertTrue(auth.contains("Signature="));
        }

        @Test
        void shouldIncludeAkInCredential() {
            Map<String, String> headers = new HashMap<>();
            headers.put("host", "example.com");

            signer.sign("GET", "/", null, headers);

            String auth = headers.get("Authorization");
            assertTrue(auth.contains("Credential=" + TEST_AK + "/"));
        }

        @Test
        void shouldIncludeRegionInCredentialScope() {
            Map<String, String> headers = new HashMap<>();
            headers.put("host", "example.com");

            signer.sign("GET", "/", null, headers);

            String auth = headers.get("Authorization");
            assertTrue(auth.contains("/" + TEST_REGION + "/apic"));
        }

        @Test
        void shouldProduceDeterministicSignatureForSameTimestamp() {
            // Use a fixed timestamp for deterministic testing
            Map<String, String> headers1 = new HashMap<>();
            headers1.put("host", "example.com");
            headers1.put("x-sdk-date", "20250615T120000Z");

            Map<String, String> headers2 = new HashMap<>();
            headers2.put("host", "example.com");
            headers2.put("x-sdk-date", "20250615T120000Z");

            signer.sign("GET", "/v1/test", null, headers1);
            signer.sign("GET", "/v1/test", null, headers2);

            assertEquals(headers1.get("Authorization"), headers2.get("Authorization"));
        }

        @Test
        void shouldProduceDifferentSignaturesForDifferentMethods() {
            Map<String, String> headers1 = new HashMap<>();
            headers1.put("host", "example.com");
            headers1.put("x-sdk-date", "20250615T120000Z");

            Map<String, String> headers2 = new HashMap<>();
            headers2.put("host", "example.com");
            headers2.put("x-sdk-date", "20250615T120000Z");

            signer.sign("GET", "/v1/test", null, headers1);
            signer.sign("POST", "/v1/test", null, headers2);

            assertNotEquals(headers1.get("Authorization"), headers2.get("Authorization"));
        }

        @Test
        void shouldProduceDifferentSignaturesForDifferentPaths() {
            Map<String, String> headers1 = new HashMap<>();
            headers1.put("host", "example.com");
            headers1.put("x-sdk-date", "20250615T120000Z");

            Map<String, String> headers2 = new HashMap<>();
            headers2.put("host", "example.com");
            headers2.put("x-sdk-date", "20250615T120000Z");

            signer.sign("GET", "/v1/test1", null, headers1);
            signer.sign("GET", "/v1/test2", null, headers2);

            assertNotEquals(headers1.get("Authorization"), headers2.get("Authorization"));
        }

        @Test
        void shouldSignWithQueryParams() {
            Map<String, String> headers = new HashMap<>();
            headers.put("host", "example.com");

            Map<String, List<String>> queryParams = new TreeMap<>();
            queryParams.put("limit", List.of("10"));
            queryParams.put("offset", List.of("0"));

            Map<String, String> result = signer.sign("GET", "/v1/agents", queryParams, headers);

            assertNotNull(result.get("Authorization"));
        }
    }

    // ========================
    // String to Sign format
    // ========================

    @Nested
    @DisplayName("String to Sign")
    class StringToSignTests {

        @Test
        void shouldStartWithAlgorithm() {
            String sts = signer.buildStringToSign("canonical-request", "20250615T120000Z", "20250615/cn-southwest-2/apic");
            assertTrue(sts.startsWith("V11-HMAC-SHA256\n"));
        }

        @Test
        void shouldContainFourLines() {
            String sts = signer.buildStringToSign("canonical-request", "20250615T120000Z", "20250615/cn-southwest-2/apic");
            String[] lines = sts.split("\n");
            assertEquals(4, lines.length);
            assertEquals("V11-HMAC-SHA256", lines[0]);
            assertEquals("20250615T120000Z", lines[1]);
            assertEquals("20250615/cn-southwest-2/apic", lines[2]);
            // line 3 is SHA-256 hex of "canonical-request"
            assertEquals(64, lines[3].length());
        }
    }

    // ========================
    // Cross-language golden vector (generated from Python signer_v11.py)
    // ========================

    @Nested
    @DisplayName("Cross-language Golden Vector")
    class CrossLanguageTests {

        private static final String GOLDEN_AK = "MYTESTAK123456789";
        private static final String GOLDEN_SK = "MYTESTSK12345678901234567890AB";
        private static final String GOLDEN_REGION = "cn-southwest-2";
        private static final String GOLDEN_TS = "20250615T120000Z";

        private final V11Signer goldenSigner = new V11Signer(GOLDEN_AK, GOLDEN_SK, GOLDEN_REGION);

        @Test
        void credentialScopeMatchesPython() {
            assertEquals("20250615/cn-southwest-2/apic",
                    goldenSigner.buildCredentialScope(GOLDEN_TS));
        }

        @Test
        void canonicalUriMatchesPython() {
            assertEquals("/v1/runtimes/", goldenSigner.canonicalUri("/v1/runtimes"));
        }

        @Test
        void canonicalQueryStringEmptyMatchesPython() {
            assertEquals("", goldenSigner.canonicalQueryString(null));
        }

        @Test
        void realUseSecretMatchesPython() {
            String scope = goldenSigner.buildCredentialScope(GOLDEN_TS);
            String rus = goldenSigner.getRealUseSecret(scope);
            assertEquals("2d2f54f2ff30f70a1dfd6336f397daabbba3d44b087a1332663ed87c8fdd48c5", rus);
        }

        @Test
        void fullSignatureMatchesPython_GET_v1_runtimes() {
            // Golden vector from Python signer_v11.py:
            // GET /v1/runtimes, host=agentarts.cn-southwest-2.myhuaweicloud.com, Content-Type=application/json
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("host", "agentarts.cn-southwest-2.myhuaweicloud.com");
            headers.put("Content-Type", "application/json");
            headers.put("x-sdk-date", GOLDEN_TS); // inject fixed timestamp

            goldenSigner.sign("GET", "/v1/runtimes", null, headers);

            String expected = "V11-HMAC-SHA256 Credential=MYTESTAK123456789/20250615/cn-southwest-2/apic, "
                    + "SignedHeaders=content-type;host;x-sdk-date, "
                    + "Signature=fbf32d0c26dc2c2abd4d727e90db1e3d9f84b2a890a9588266d4d8ce9f1f0c62";
            assertEquals(expected, headers.get("Authorization"));
        }

        @Test
        void signatureIgnoresQueryParams() {
            // Query params are excluded from the canonical request: the current
            // signing scheme does not support signing query params, so the
            // signature for POST /v1/agents?limit=10&offset=0&tag=a&tag=b
            // equals the signature of the same request without any query.
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("host", "example.com");
            headers.put("x-sdk-date", GOLDEN_TS);

            Map<String, List<String>> qp = new java.util.LinkedHashMap<>();
            qp.put("limit", List.of("10"));
            qp.put("offset", List.of("0"));
            qp.put("tag", List.of("b", "a"));

            goldenSigner.sign("POST", "/v1/agents", qp, headers);

            String expected = "V11-HMAC-SHA256 Credential=MYTESTAK123456789/20250615/cn-southwest-2/apic, "
                    + "SignedHeaders=host;x-sdk-date, "
                    + "Signature=823be3a8559b18fb01843a23a76989081ab4efa4ac4cc903c5f62bc7d536fe71";
            assertEquals(expected, headers.get("Authorization"));
        }

        @Test
        void signatureIgnoresQueryParamsForUploadPath() {
            // An upload-files call whose query value is a remote path
            // (/runtimes/{name}/upload-files?path=/home/user/pom.xml): query
            // params are excluded from the canonical request, so the path value
            // does not affect the signature.
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("host", "aigateway-mm2gin3dgq.cn-southwest-2.huaweicloud-agentarts.com");
            headers.put("x-sdk-date", "20260910T000000Z");

            Map<String, List<String>> qp = new java.util.LinkedHashMap<>();
            qp.put("path", List.of("/home/user/pom.xml"));

            goldenSigner.sign("POST", "/runtimes/myagent-lvokc/upload-files", qp, headers);

            String expected = "V11-HMAC-SHA256 Credential=MYTESTAK123456789/20260910/cn-southwest-2/apic, "
                    + "SignedHeaders=host;x-sdk-date, "
                    + "Signature=e98a7232e8f794198462a9f76db7eb0837fe3b128d03d64e74cdc153c47c6182";
            assertEquals(expected, headers.get("Authorization"));
        }
    }

    // ========================
    // Bytes/Hex conversion
    // ========================

    @Nested
    @DisplayName("Byte/Hex Conversion")
    class BytesHexTests {

        @Test
        void shouldConvertBytesToHex() {
            byte[] bytes = {0x00, (byte) 0xff, 0x0a, (byte) 0xbc};
            assertEquals("00ff0abc", V11Signer.bytesToHex(bytes));
        }

        @Test
        void shouldConvertEmptyBytes() {
            assertEquals("", V11Signer.bytesToHex(new byte[0]));
        }
    }
}
