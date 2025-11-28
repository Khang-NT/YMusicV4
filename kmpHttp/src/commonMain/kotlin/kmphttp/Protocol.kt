package kmphttp

import okio.IOException
import kotlin.jvm.JvmStatic
/**
 * Protocols for [ALPN][ietf_alpn] selection.
 *
 * ## Protocol vs Scheme
 *
 * Note that "protocol" identifies how HTTP messages are framed (http/1.1, h2, etc.), not the URL
 * scheme (http, https, etc.).
 *
 * [ietf_alpn]: http://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg
 */
expect enum class Protocol {

    /**
     * An obsolete plaintext framing that does not use persistent sockets by default.
     */
    HTTP_1_0,

    /**
     * A plaintext framing that includes persistent connections.
     *
     * This version of OkHttp implements [RFC 7230][rfc_7230], and tracks revisions to that spec.
     *
     * [rfc_7230]: https://tools.ietf.org/html/rfc7230
     */
    HTTP_1_1,

    /**
     * The IETF's binary-framed protocol that includes header compression, multiplexing multiple
     * requests on the same socket, and server-push. HTTP/1.1 semantics are layered on HTTP/2.
     *
     * HTTP/2 requires deployments of HTTP/2 that use TLS 1.2 support
     * [CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256], present in Java 8+ and Android 5+.
     * Servers that enforce this may send an exception message including the string
     * `INADEQUATE_SECURITY`.
     */
    HTTP_2,

    /**
     * Cleartext HTTP/2 with no "upgrade" round trip. This option requires the client to have prior
     * knowledge that the server supports cleartext HTTP/2.
     *
     * See also [Starting HTTP/2 with Prior Knowledge][rfc_7540_34].
     *
     * [rfc_7540_34]: https://datatracker.ietf.org/doc/html/rfc7540#autoid-10
     */
    H2_PRIOR_KNOWLEDGE,

    /**
     * QUIC (Quick UDP Internet Connection) is a new multiplexed and secure transport atop UDP,
     * designed from the ground up and optimized for HTTP/2 semantics. HTTP/1.1 semantics are layered
     * on HTTP/2.
     *
     * QUIC is not natively supported, but provided to allow a theoretical interceptor that provides
     * support.
     */
    QUIC,

    /**
     * HTTP/3 is the third and upcoming major version of the Hypertext Transfer Protocol used to
     * exchange information. HTTP/3 runs over QUIC, which is published as RFC 9000.
     *
     * HTTP/3 is not natively supported, but provided to allow a theoretical interceptor that
     * provides support.
     */
    HTTP_3,
    ;
    /**
     * Returns the string used to identify this protocol for ALPN, like "http/1.1", "spdy/3.1" or
     * "h2".
     *
     * See also [IANA tls-extensiontype-values][iana].
     *
     * [iana]: https://www.iana.org/assignments/tls-extensiontype-values
     */
    override fun toString(): String

    companion object {
        /**
         * Returns the protocol identified by `protocol`.
         *
         * @throws IOException if `protocol` is unknown.
         */
        fun get(protocol: String): Protocol
    }
}
