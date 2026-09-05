package com.huaweicloud.agentarts.sdk.core;

/**
 * Signing mode for HTTP requests.
 *
 * <p>Determines which signing algorithm is used when authenticating requests
 * to AgentArts endpoints.</p>
 *
 * <ul>
 *   <li>{@link #SDK_HMAC_SHA256} — Standard Huawei Cloud SDK-HMAC-SHA256 signing
 *       (used for Huawei Cloud standard services via SDK built-in signer).</li>
 *   <li>{@link #V11_HMAC_SHA256} — AgentArts V11-HMAC-SHA256 signing with HKDF
 *       key derivation (used for AgentArts self-developed endpoints: control plane,
 *       data plane, runtime invoke, code interpreter, gateway).</li>
 * </ul>
 *
 * @see com.huaweicloud.agentarts.sdk.core.signer.V11Signer
 */
public enum SignMode {

    /** Standard Huawei Cloud SDK-HMAC-SHA256 signing. */
    SDK_HMAC_SHA256("sdk"),

    /** AgentArts V11-HMAC-SHA256 signing with HKDF key derivation. */
    V11_HMAC_SHA256("v11");

    private final String value;

    SignMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
