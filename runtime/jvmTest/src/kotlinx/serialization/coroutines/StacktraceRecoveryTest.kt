/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.coroutines

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.internal.*
import kotlin.test.*

class StacktraceRecoveryTest {
    @Serializable
    private class Data(val s: String)

    private class BadDecoder : AbstractDecoder() {
        override fun decodeElementIndex(descriptor: SerialDescriptor): Int = 42
    }

    @Test
    fun testJsonDecodingException() = checkRecovered<JsonDecodingException> {
        Json.decodeFromString<String>("42")
    }

    @Test
    fun testJsonEncodingException() = checkRecovered<JsonEncodingException> {
        Json.encodeToString(Double.NaN)
    }

    @Test
    @Ignore // fixme after 1.4.20 plugin with support for new exception ctor signatures
    fun testUnknownFieldException() = checkRecovered<UnknownFieldException> {
        val serializer = Data.serializer()
        serializer.deserialize(BadDecoder())
    }

    @Test
    @Ignore // fixme after 1.4.20 plugin with support for new exception ctor signatures
    fun testMissingFieldException() = checkRecovered<MissingFieldException> {
        Json.decodeFromString<Data>("{}")
    }

    private inline fun <reified E : Exception> checkRecovered(noinline block: () -> Unit) = runBlocking {
        val result = kotlin.runCatching {
            // use withContext to perform switch between coroutines and thus trigger exception recovery machinery
            withContext(NonCancellable) {
                block()
            }
        }
        assertTrue(result.isFailure, "Block should have failed")
        val e = result.exceptionOrNull()!!
        assertEquals(E::class, e::class)
        val cause = e.cause
        assertNotNull(cause, "Exception should have cause: $e")
        assertEquals(e.message, cause.message)
        assertEquals(E::class, cause::class)
    }
}