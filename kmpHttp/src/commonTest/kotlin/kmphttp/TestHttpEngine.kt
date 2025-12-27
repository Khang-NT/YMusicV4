package kmphttp

/**
 * Creates an HttpEngine for testing purposes.
 * Each platform provides its own implementation.
 */
expect fun createHttpEngineForTest(): HttpEngine
