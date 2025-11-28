package kmphttp.internal
object HttpMethod {
  fun invalidatesCache(method: String): Boolean =
    (
      method == "POST" ||
        method == "PATCH" ||
        method == "PUT" ||
        method == "DELETE" ||
        method == "MOVE"
    )

  fun requiresRequestBody(method: String): Boolean =
    (
      method == "POST" ||
        method == "PUT" ||
        method == "PATCH" ||
        method == "PROPPATCH" ||
        method == "QUERY" ||
        // WebDAV
        method == "REPORT"
    )

  fun permitsRequestBody(method: String): Boolean = !(method == "GET" || method == "HEAD")

  fun redirectsWithBody(method: String): Boolean = method == "PROPFIND"

  fun redirectsToGet(method: String): Boolean = method != "PROPFIND"

  fun isCacheable(requestMethod: String): Boolean = requestMethod == "GET" || requestMethod == "QUERY"
}