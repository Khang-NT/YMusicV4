package kmphttp

actual typealias Cookie = okhttp3.Cookie
actual typealias CookieBuilder = okhttp3.Cookie.Builder

actual suspend fun parseCookie(
    url: HttpUrl,
    setCookie: String,
): Cookie? = Cookie.parse(url, setCookie)

actual suspend fun parseAllCookies(
    url: HttpUrl,
    headers: Headers,
): List<Cookie> = Cookie.parseAll(url, headers)