/*
 * Copyright (C) 2014 Square, Inc.
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
package kmphttp

import okio.IOException

actual enum class Protocol(
  private val protocol: String,
) {
  HTTP_1_0("http/1.0"),
  HTTP_1_1("http/1.1"),
  @Deprecated("Dropped support for SPDY. Prefer HTTP_2.")
  SPDY_3("spdy/3.1"),
  HTTP_2("h2"),
  H2_PRIOR_KNOWLEDGE("h2_prior_knowledge"),
  QUIC("quic"),
  HTTP_3("h3"),
  ;

  actual override fun toString(): String = protocol

  actual companion object {
    actual fun get(protocol: String): Protocol {
      // Unroll the loop over values() to save an allocation.
      @Suppress("DEPRECATION")
      return when (protocol) {
        HTTP_1_0.protocol -> HTTP_1_0
        HTTP_1_1.protocol -> HTTP_1_1
        H2_PRIOR_KNOWLEDGE.protocol -> H2_PRIOR_KNOWLEDGE
        HTTP_2.protocol -> HTTP_2
        SPDY_3.protocol -> SPDY_3
        QUIC.protocol -> QUIC
        else -> {
          // Support HTTP3 draft like h3-29
          if (protocol.startsWith(HTTP_3.protocol)) HTTP_3 else throw IOException("Unexpected protocol: $protocol")
        }
      }
    }
  }
}
