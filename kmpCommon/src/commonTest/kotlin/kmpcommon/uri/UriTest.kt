package kmpcommon.uri

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for RFC 3986 URI implementation.
 */
class UriTest {

    // ==========================================================================
    // Section 1.1.2: Example URIs
    // ==========================================================================

    @Test
    fun `parse RFC example URIs`() {
        // ftp://ftp.is.co.za/rfc/rfc1808.txt
        val ftp = Uri.parse("ftp://ftp.is.co.za/rfc/rfc1808.txt")
        assertEquals("ftp", ftp.scheme)
        assertEquals("ftp.is.co.za", ftp.authority)
        assertEquals("/rfc/rfc1808.txt", ftp.encodedPath)

        // http://www.ietf.org/rfc/rfc2396.txt
        val http = Uri.parse("http://www.ietf.org/rfc/rfc2396.txt")
        assertEquals("http", http.scheme)
        assertEquals("www.ietf.org", http.authority)

        // ldap://[2001:db8::7]/c=GB?objectClass?one
        val ldap = Uri.parse("ldap://[2001:db8::7]/c=GB?objectClass?one")
        assertEquals("ldap", ldap.scheme)
        assertEquals("[2001:db8::7]", ldap.authority)
        assertEquals("/c=GB", ldap.encodedPath)
        assertEquals("objectClass?one", ldap.encodedQuery)

        // mailto:John.Doe@example.com
        val mailto = Uri.parse("mailto:John.Doe@example.com")
        assertEquals("mailto", mailto.scheme)
        assertEquals("John.Doe@example.com", mailto.encodedPath)
        assertNull(mailto.authority)

        // tel:+1-816-555-1212
        val tel = Uri.parse("tel:+1-816-555-1212")
        assertEquals("tel", tel.scheme)
        assertEquals("+1-816-555-1212", tel.encodedPath)

        // telnet://192.0.2.16:80/
        val telnet = Uri.parse("telnet://192.0.2.16:80/")
        assertEquals("telnet", telnet.scheme)
        assertEquals("192.0.2.16:80", telnet.authority)

        // urn:oasis:names:specification:docbook:dtd:xml:4.1.2
        val urn = Uri.parse("urn:oasis:names:specification:docbook:dtd:xml:4.1.2")
        assertEquals("urn", urn.scheme)
        assertEquals("oasis:names:specification:docbook:dtd:xml:4.1.2", urn.encodedPath)
    }

    // ==========================================================================
    // Section 3: Syntax Components
    // ==========================================================================

    @Test
    fun `parse URI with all components`() {
        val uri = Uri.parse("foo://example.com:8042/over/there?name=ferret#nose")
        assertEquals("foo", uri.scheme)
        assertEquals("example.com:8042", uri.authority)
        assertEquals("/over/there", uri.encodedPath)
        assertEquals("name=ferret", uri.encodedQuery)
        assertEquals("nose", uri.encodedFragment)
    }

    @Test
    fun `parse opaque URI`() {
        val uri = Uri.parse("urn:example:animal:ferret:nose")
        assertEquals("urn", uri.scheme)
        assertNull(uri.authority)
        assertEquals("example:animal:ferret:nose", uri.encodedPath)
        assertTrue(uri.isOpaque)
    }

    // ==========================================================================
    // Section 3.1: Scheme
    // ==========================================================================

    @Test
    fun `scheme validation - valid schemes`() {
        assertNotNull(Uri.parse("a://example.com"))
        assertNotNull(Uri.parse("a1://example.com"))
        assertNotNull(Uri.parse("a+b://example.com"))
        assertNotNull(Uri.parse("a-b://example.com"))
        assertNotNull(Uri.parse("a.b://example.com"))
        assertNotNull(Uri.parse("HTTP://example.com")) // Case insensitive
    }

    @Test
    fun `scheme validation - invalid schemes`() {
        assertFailsWith<UriSyntaxException> { Uri.parse("1http://example.com") }
        assertFailsWith<UriSyntaxException> { Uri.parse("+http://example.com") }
    }

    // ==========================================================================
    // Section 3.2: Authority
    // ==========================================================================

    @Test
    fun `parse authority with userinfo`() {
        val uri = Uri.parse("http://user:pass@example.com/")
        assertEquals("user:pass@example.com", uri.authority)
    }

    @Test
    fun `parse authority with IPv4 address`() {
        val uri = Uri.parse("http://192.168.1.1/")
        assertEquals("192.168.1.1", uri.authority)
    }

    @Test
    fun `parse authority with IPv6 address`() {
        val uri = Uri.parse("http://[::1]/")
        assertEquals("[::1]", uri.authority)

        val uri2 = Uri.parse("http://[2001:db8:85a3::8a2e:370:7334]/")
        assertEquals("[2001:db8:85a3::8a2e:370:7334]", uri2.authority)
    }

    @Test
    fun `parse authority with port`() {
        val uri = Uri.parse("http://example.com:8080/")
        assertEquals("example.com:8080", uri.authority)
    }

    // ==========================================================================
    // Section 3.3: Path
    // ==========================================================================

    @Test
    fun `parse various path formats`() {
        // path-abempty (begins with "/" or is empty)
        assertEquals("/a/b/c", Uri.parse("http://example.com/a/b/c").encodedPath)
        assertEquals("/", Uri.parse("http://example.com/").encodedPath)

        // path-empty
        assertEquals("", Uri.parse("http://example.com").encodedPath)

        // path-absolute (begins with "/" but not "//")
        assertEquals("/a/b/c", Uri.parse("file:///a/b/c").encodedPath)

        // path-rootless (begins with a segment)
        assertEquals("a:b:c", Uri.parse("urn:a:b:c").encodedPath)
    }

    @Test
    fun `path with special characters`() {
        val uri = Uri.parse("http://example.com/path%20with%20spaces")
        assertEquals("/path with spaces", uri.path.toString())
    }

    // ==========================================================================
    // Section 3.4: Query
    // ==========================================================================

    @Test
    fun `parse query component`() {
        val encodedQuery = "key=value&foo=bar&a%20b&x=1&x=2&x="
        val uri = Uri.parse("http://example.com/path?$encodedQuery")
        assertEquals(encodedQuery, uri.encodedQuery)
        assertEquals("value", uri.queryParamValue("key"))
        assertEquals("bar", uri.queryParamValue("foo"))
        assertEquals(null, uri.queryParamValue("a b"))
        assertEquals(listOf("1", "2", ""), uri.queryParamValues("x")?.map { it.toString() })
    }

    @Test
    fun `query can contain slash and question mark`() {
        val uri = Uri.parse("http://example.com?q=a/b?=c")
        assertEquals("q=a/b?=c", uri.encodedQuery)
        assertEquals("a/b?=c", uri.queryParamValue("q"))
    }

    @Test
    fun `without question mark query should be null`() {
        val uri = Uri.parse("http://example.com")
        assertNull(uri.queryNames())
        assertNull(uri.encodedQuery)
    }

    // ==========================================================================
    // Section 3.5: Fragment
    // ==========================================================================

    @Test
    fun `parse fragment component`() {
        val uri = Uri.parse("http://example.com/path#section1")
        assertEquals("section1", uri.encodedFragment)
    }

    @Test
    fun `fragment can contain slash and question mark`() {
        val uri = Uri.parse("http://example.com#a/b?c")
        assertEquals("a/b?c", uri.encodedFragment)
    }

    // ==========================================================================
    // Section 4.2: Relative Reference
    // ==========================================================================

    @Test
    fun `parse relative references`() {
        // Network-path reference
        val networkPath = Uri.parse("//example.com/path")
        assertNull(networkPath.scheme)
        assertEquals("example.com", networkPath.authority)

        // Absolute-path reference
        val absolutePath = Uri.parse("/path/to/resource")
        assertNull(absolutePath.scheme)
        assertNull(absolutePath.authority)
        assertEquals("/path/to/resource", absolutePath.path.toString())

        // Relative-path reference
        val relativePath = Uri.parse("path/to/resource")
        assertNull(relativePath.scheme)
        assertEquals("path/to/resource", relativePath.path.toString())

        // Query-only reference
        val queryOnly = Uri.parse("?query")
        assertEquals("", queryOnly.path.toString())
        assertEquals("query", queryOnly.encodedQuery)

        // Fragment-only reference
        val fragmentOnly = Uri.parse("#fragment")
        assertEquals("", fragmentOnly.path.toString())
        assertEquals("fragment", fragmentOnly.encodedFragment)
    }

    // ==========================================================================
    // Section 2.1: Percent-Encoding
    // ==========================================================================

    @Test
    fun `percent encoding - encode unreserved characters`() {
        val input = "hello world"
        val encoded = PercentEncoder.canonicalize(input, emptySet())
        assertEquals("hello%20world", encoded.toString())
    }

    @Test
    fun `percent encoding - decode`() {
        assertEquals("hello world", PercentEncoder.decode("hello%20world").toString())
        assertEquals("hello%world", PercentEncoder.decode("hello%25world").toString())
    }

    @Test
    fun `percent encoding - uppercase hex digits`() {
        assertEquals("%2F", PercentEncoder.canonicalize("/", emptySet()).toString())
    }

    @Test
    fun `percent encoding - UTF-8 characters`() {
        val cafe = "café🥦"
        val encoded = PercentEncoder.canonicalize(cafe, emptySet())
        assertEquals("caf%C3%A9%F0%9F%A5%A6", encoded.toString())
        assertEquals(cafe, PercentEncoder.decode(encoded).toString())
    }

    // ==========================================================================
    // URI Builder Tests
    // ==========================================================================

    @Test
    fun `builder creates correct URI`() {
        val uri = UriBuilder()
            .scheme("https")
            .authority("example.com:8080")
            .path("/api")
            .appendPath("/users")
            .appendQueryParam("page", "1")
            .appendQueryParam("size", "10")
            .fragment("top")
            .build()

        assertEquals("https", uri.scheme.toString())
        assertEquals("example.com:8080", uri.authority.toString())
        assertEquals("/api/users", uri.path.toString())
        assertEquals("1", uri.queryParamValue("page")?.toString())
        assertEquals("10", uri.queryParamValue("size")?.toString())
        assertEquals("top", uri.fragment.toString())
    }

    @Test
    fun `builder with path segments`() {
        val uri = UriBuilder()
            .scheme("https")
            .authority("example.com")
            .appendPathSegment("api")
            .appendPathSegment("users")
            .appendPathSegment("123")
            .build()

        assertEquals("/api/users/123", uri.path.toString())
    }

    @Test
    fun `builder from existing URI`() {
        val original = Uri.parse("https://example.com/path?query=value#frag")
        val modified = original.newBuilder()
            .appendQueryParam("extra", "param")
            .build()

        assertEquals("param", modified.queryParamValue("extra"))
    }

    // ==========================================================================
    // Component Recomposition (Section 5.3)
    // ==========================================================================

    @Test
    fun `toString produces valid URI string`() {
        val input = "http://user:pass@example.com:8080/path?query=value#fragment"
        val uri = Uri.parse(input)
        val toString = uri.toString()

        assertTrue(toString.contains("//"))
        assertTrue(toString.contains("user:pass@"))
        assertTrue(toString.contains("example.com:8080"))
        assertTrue(toString.contains("/path"))
        assertTrue(toString.contains("?query=value"))
        assertTrue(toString.contains("#fragment"))
    }

    @Test
    fun `roundtrip parsing`() {
        val uris = listOf(
            "http://example.com",
            "http://example.com/",
            "http://example.com/path",
            "http://example.com/path?query",
            "http://example.com/path?query#fragment",
            "http://user@example.com/",
            "http://user:pass@example.com/",
            "http://[::1]/",
            "http://192.168.1.1/",
            "mailto:user@example.com",
            "urn:isbn:0451450523"
        )

        for (uriStr in uris) {
            val parsed = Uri.parse(uriStr)
            val reparsed = Uri.parse(parsed.toString())
            assertEquals(parsed.scheme?.toString(), reparsed.scheme?.toString(), "Scheme mismatch for $uriStr")
            assertEquals(parsed.authority?.toString(), reparsed.authority?.toString(), "Authority mismatch for $uriStr")
            assertEquals(parsed.path.toString(), reparsed.path.toString(), "Path mismatch for $uriStr")
            assertEquals(parsed.encodedQuery?.toString(), reparsed.encodedQuery?.toString(), "Query mismatch for $uriStr")
            assertEquals(parsed.fragment?.toString(), reparsed.fragment?.toString(), "Fragment mismatch for $uriStr")
        }
    }

    // ==========================================================================
    // Edge Cases and Error Handling
    // ==========================================================================

    @Test
    fun `empty URI`() {
        val uri = Uri.parse("")
        assertNull(uri.scheme)
        assertNull(uri.authority)
        assertEquals("", uri.path.toString())
        assertNull(uri.queryNames())
        assertNull(uri.fragment)
    }

    @Test
    fun `URI with empty components`() {
        // Empty authority
        val emptyAuthority = Uri.parse("file:///path")
        assertEquals("", emptyAuthority.authority)

        // Empty query
        val emptyQuery = Uri.parse("http://example.com?")
        assertEquals("", emptyQuery.encodedQuery)

        // Empty fragment
        val emptyFragment = Uri.parse("http://example.com#")
        assertEquals("", emptyFragment.fragment)
    }

    @Test
    fun `URI properties`() {
        val absolute = Uri.parse("http://example.com/")
        assertTrue(absolute.isAbsolute)
        assertFalse(absolute.isOpaque)

        val relative = Uri.parse("/path/to/resource")
        assertFalse(relative.isAbsolute)

        val opaque = Uri.parse("mailto:user@example.com")
        assertTrue(opaque.isOpaque)
    }

    // ==========================================================================
    // Section 5.4: Reference Resolution Examples
    // ==========================================================================

    /**
     * RFC 3986 Section 5.4 test cases.
     * Base URI: "http://a/b/c/d;p?q"
     */
    @Test
    fun `resolve normal examples from RFC 3986 section 5-4-1`() {
        val base = Uri.parse("http://a/b/c/d;p?q")

        // Normal Examples
        assertEquals("http://a/b/c/g", base.resolve("g").toString())
        assertEquals("http://a/b/c/g", base.resolve("./g").toString())
        assertEquals("http://a/b/c/g/", base.resolve("g/").toString())
        assertEquals("http://a/g", base.resolve("/g").toString())
        assertEquals("http://g", base.resolve("//g").toString())
        assertEquals("http://a/b/c/d;p?y", base.resolve("?y").toString())
        assertEquals("http://a/b/c/g?y", base.resolve("g?y").toString())
        assertEquals("http://a/b/c/d;p?q#s", base.resolve("#s").toString())
        assertEquals("http://a/b/c/g#s", base.resolve("g#s").toString())
        assertEquals("http://a/b/c/g?y#s", base.resolve("g?y#s").toString())
        assertEquals("http://a/b/c/;x", base.resolve(";x").toString())
        assertEquals("http://a/b/c/g;x", base.resolve("g;x").toString())
        assertEquals("http://a/b/c/g;x?y#s", base.resolve("g;x?y#s").toString())
        assertEquals("http://a/b/c/d;p?q", base.resolve("").toString())
        assertEquals("http://a/b/c/", base.resolve(".").toString())
        assertEquals("http://a/b/c/", base.resolve("./").toString())
        assertEquals("http://a/b/", base.resolve("..").toString())
        assertEquals("http://a/b/", base.resolve("../").toString())
        assertEquals("http://a/b/g", base.resolve("../g").toString())
        assertEquals("http://a/", base.resolve("../..").toString())
        assertEquals("http://a/", base.resolve("../../").toString())
        assertEquals("http://a/g", base.resolve("../../g").toString())
    }

    @Test
    fun `resolve abnormal examples from RFC 3986 section 5-4-2`() {
        val base = Uri.parse("http://a/b/c/d;p?q")

        // Abnormal Examples - going above root should be removed
        assertEquals("http://a/g", base.resolve("../../../g").toString())
        assertEquals("http://a/g", base.resolve("../../../../g").toString())

        // Not starting with /
        assertEquals("http://a/g", base.resolve("/./g").toString())
        assertEquals("http://a/g", base.resolve("/../g").toString())
        assertEquals("http://a/b/c/g.", base.resolve("g.").toString())
        assertEquals("http://a/b/c/.g", base.resolve(".g").toString())
        assertEquals("http://a/b/c/g..", base.resolve("g..").toString())
        assertEquals("http://a/b/c/..g", base.resolve("..g").toString())

        // Nonsensical forms
        assertEquals("http://a/b/g", base.resolve("./../g").toString())
        assertEquals("http://a/b/c/g/", base.resolve("./g/.").toString())
        assertEquals("http://a/b/c/g/h", base.resolve("g/./h").toString())
        assertEquals("http://a/b/c/h", base.resolve("g/../h").toString())
        assertEquals("http://a/b/c/g;x=1/y", base.resolve("g;x=1/./y").toString())
        assertEquals("http://a/b/c/y", base.resolve("g;x=1/../y").toString())

        // Query and fragment
        assertEquals("http://a/b/c/g?y/./x", base.resolve("g?y/./x").toString())
        assertEquals("http://a/b/c/g?y/../x", base.resolve("g?y/../x").toString())
        assertEquals("http://a/b/c/g#s/./x", base.resolve("g#s/./x").toString())
        assertEquals("http://a/b/c/g#s/../x", base.resolve("g#s/../x").toString())
    }

    @Test
    fun `resolve with absolute reference preserves scheme`() {
        val base = Uri.parse("http://example.com/path")
        val resolved = base.resolve("https://other.com/new")
        assertEquals("https://other.com/new", resolved.toString())
    }

    @Test
    fun `resolve network-path reference`() {
        val base = Uri.parse("http://example.com/path")
        val resolved = base.resolve("//other.com/new")
        assertEquals("http://other.com/new", resolved.toString())
    }

    @Test
    fun `resolve with query only`() {
        val base = Uri.parse("http://example.com/path?old=query")
        val resolved = base.resolve("?new=query")
        assertEquals("http://example.com/path?new=query", resolved.toString())
    }

    @Test
    fun `resolve with fragment only`() {
        val base = Uri.parse("http://example.com/path?query")
        val resolved = base.resolve("#section")
        assertEquals("http://example.com/path?query#section", resolved.toString())
    }

    @Test
    fun `resolve with base having no path`() {
        val base = Uri.parse("http://example.com")
        assertEquals("http://example.com/g", base.resolve("g").toString())
        assertEquals("http://example.com/g", base.resolve("/g").toString())
        assertEquals("http://example.com/g", base.resolve("./g").toString())
    }

    @Test
    fun `removeDotSegments standalone tests`() {
        assertEquals("/a/g", Uri.removeDotSegments("/a/b/c/./../../g"))
        assertEquals("mid/6", Uri.removeDotSegments("mid/content=5/../6"))
        assertEquals("/", Uri.removeDotSegments("/a/b/../../"))
        assertEquals("/a/b/c", Uri.removeDotSegments("/a/b/c"))
        assertEquals("", Uri.removeDotSegments(""))
        assertEquals("/", Uri.removeDotSegments("/."))
        assertEquals("/", Uri.removeDotSegments("/.."))
        assertEquals("/abc/", Uri.removeDotSegments("/abc/."))
        assertEquals("/abc/", Uri.removeDotSegments("/abc/xyz/.."))
    }
}
