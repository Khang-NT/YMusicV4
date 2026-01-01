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

import okio.Buffer
import okio.ByteString

/**
 * A minimal public suffix list for iOS testing.
 * Contains common TLDs and wildcards for basic cookie/URL tests.
 *
 * Suffixes are sorted alphabetically and newline-terminated.
 */
private object MinimalPublicSuffixList : PublicSuffixList {
    override suspend fun ensureLoaded() {
        // Already loaded - no-op
    }

    /**
     * Public suffix rules (sorted alphabetically):
     * - Standard TLDs: com, net, org, etc.
     * - Country codes: cn, jp, uk, etc.
     * - Wildcards: *.amazonaws.com, *.elb.amazonaws.com, etc.
     */
    override val bytes: ByteString = Buffer()
        // Wildcards and multi-level suffixes (sorted)
        .writeUtf8("*.ck\n")
        .writeUtf8("*.elb.amazonaws.com\n")
        .writeUtf8("*.github.io\n")
        .writeUtf8("*.jp\n")
        .writeUtf8("*.mm\n")
        .writeUtf8("*.uk\n")
        .writeUtf8("ac\n")
        .writeUtf8("ac.jp\n")
        .writeUtf8("ac.uk\n")
        .writeUtf8("amazonaws.com\n")
        .writeUtf8("biz\n")
        .writeUtf8("ck\n")
        .writeUtf8("cn\n")
        .writeUtf8("co.jp\n")
        .writeUtf8("co.uk\n")
        .writeUtf8("com\n")
        .writeUtf8("com.cn\n")
        .writeUtf8("github.io\n")
        .writeUtf8("io\n")
        .writeUtf8("jp\n")
        .writeUtf8("mm\n")
        .writeUtf8("net\n")
        .writeUtf8("org\n")
        .writeUtf8("uk\n")
        .writeUtf8("uk.com\n")
        .writeUtf8("us\n")
        // Unicode/IDN suffixes (sorted by punycode)
        .writeUtf8("xn--55qx5d.cn\n") // 公司.cn
        .writeUtf8("xn--8ltr62k.jp\n") // 長崎.jp
        .writeUtf8("xn--fiqs8s\n") // 中国
        .readByteString()

    /**
     * Exception rules - domains that are NOT public suffixes even though
     * they match wildcard rules.
     */
    override val exceptionBytes: ByteString = Buffer()
        .writeUtf8("www.ck\n")
        .readByteString()
}

internal actual val PublicSuffixList.Companion.Default: PublicSuffixList
    get() = MinimalPublicSuffixList