/*
 * Copyright (C) 2017 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kmphttp.internal.publicsuffix

import okio.ByteString

/**
 * A configurable PublicSuffixList for testing.
 */
internal class ConfiguredPublicSuffixList : PublicSuffixList {
    override suspend fun ensureLoaded() {
        // Already loaded - no-op for tests
    }

    override var bytes: ByteString = ByteString.EMPTY
    override var exceptionBytes: ByteString = ByteString.EMPTY
}
