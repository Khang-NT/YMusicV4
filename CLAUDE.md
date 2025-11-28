# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This YMusic V4 project, which will rewrite completely @YMusicLegacy with new tech stacks. Reference @YMusicLegacy for legacy code.

## Coding Rules

### Error Handling
- **Never catch and silently ignore exceptions.** Methods should throw or return exceptions with meaningful error messages to the caller.

### Coroutines
- **Suspend functions must be safe to call from main thread.** Use `withContext(Dispatchers.IO)` for I/O operations or `withContext(Dispatchers.Default)` for CPU-bound work.
- **Mutex is NOT reentrant** - nested acquisition deadlocks. Use with limited scope:
  - Guard specific state only, don't expose mutex
  - Keep critical sections small, no nested lock calls
  - Example pattern:
    ```kotlin
    class SafeState<T>(initial: T) {
        private var value = initial
        private val mutex = Mutex()
        fun get() = value  // non-blocking read
        suspend fun update(block: (T) -> T) = mutex.withLock { value = block(value) }
    }
    ```
  - For complex scenarios: use `limitedParallelism` or actor/channel pattern

### Common mistakes
- Don't use kotlin extension `Char.isLetterOrDigit` to check whether a Char is `[a-zA-Z0-9]`, because it includes non alphabet letters like `'β'.isLetterOrDigit() == true`.