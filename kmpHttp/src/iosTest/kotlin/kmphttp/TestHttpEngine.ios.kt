package kmphttp


actual fun createHttpEngineForTest(): HttpEngine {
    return UrlSessionEngine()
}
