package kmphttp

object HttpStatus {
    const val HTTP_CONTINUE = 100
    const val HTTP_BAD_METHOD = 405
    const val HTTP_GONE = 410
    const val HTTP_MOVED_PERM = 301
    const val HTTP_MOVED_TEMP = 302
    const val HTTP_SEE_OTHER = 303
    const val HTTP_NOT_MODIFIED = 304
    const val HTTP_TEMP_REDIRECT = 307
    const val HTTP_PERM_REDIRECT = 308
    const val HTTP_MULT_CHOICE = 300
    const val HTTP_NOT_AUTHORITATIVE = 203
    const val HTTP_NOT_FOUND = 404
    const val HTTP_NOT_IMPLEMENTED = 501
    const val HTTP_NO_CONTENT = 204
    const val HTTP_OK = 200
    const val HTTP_REQ_TOO_LONG = 414
    const val HTTP_GATEWAY_TIMEOUT = 504
}