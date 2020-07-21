package com.juul.koap.test

import com.juul.koap.Message
import com.juul.koap.Message.Code.Method.GET
import com.juul.koap.Message.Option.Observe
import com.juul.koap.Message.Option.Observe.Registration.Deregister
import com.juul.koap.Message.Option.Observe.Registration.Register
import com.juul.koap.Message.Option.UriHost
import com.juul.koap.Message.Option.UriPath
import com.juul.koap.Message.Option.UriPort
import com.juul.koap.Message.Udp.Type.Confirmable
import com.juul.koap.decode
import com.juul.koap.encode
import com.juul.koap.readOption
import com.juul.koap.reader
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.ByteString.Companion.decodeHex

class DecoderTest {

    @Test
    fun `Decode Udp GET with Options`() {
        val message = Message.Udp(
            type = Confirmable,
            code = GET,
            id = 0xCAFE,
            token = 1,
            options = listOf(
                UriHost("http://localhost"),
                UriPort(1234),
                UriPath("/test")
            ),
            payload = "Hello UDP!".toByteArray()
        )

        assertEquals(
            expected = message,
            actual = message.encode().decode()
        )
    }

    @Test
    fun `Decode Tcp GET with Options`() {
        val message = Message.Tcp(
            code = GET,
            token = 1,
            options = listOf(
                UriHost("http://localhost"),
                UriPort(1234),
                UriPath("/test")
            ),
            payload = "Hello TCP!".toByteArray()
        )

        assertEquals(
            expected = message,
            actual = message.encode().decode()
        )
    }

    @Test
    fun `Decode Tcp GET without Options nor Payload`() {
        val message = Message.Tcp(
            code = GET,
            token = 1,
            options = emptyList(),
            payload = byteArrayOf()
        )

        assertEquals(
            expected = message,
            actual = message.encode().decode()
        )
    }

    @Test
    fun `Decode Tcp GET without Options, but with Payload`() {
        val message = Message.Tcp(
            code = GET,
            token = 1,
            options = emptyList(),
            payload = "Hello TCP!".toByteArray()
        )

        assertEquals(
            expected = message,
            actual = message.encode().decode()
        )
    }

    @Test
    fun `Decode Tcp GET with Options, but without Payload`() {
        val message = Message.Tcp(
            code = GET,
            token = 1,
            options = listOf(
                UriHost("http://localhost"),
                UriPort(1234),
                UriPath("/test")
            ),
            payload = byteArrayOf()
        )

        assertEquals(
            expected = message,
            actual = message.encode().decode()
        )
    }

    @Test
    fun `Decode Observe Option with value of 16,777,215`() {
        testReadOption(
            encoded = """
                63       # Option Delta: 6, Option Length: 3
                FF FF FF # Option Value: 16,777,215
            """,
            expected = Observe(16_777_215)
        )
    }

    @Test
    fun `Decode Observe Option with value of Register`() {
        testReadOption(
            encoded = """
                60 # Option Delta: 6, Option Length: 0 (Option Value of 0 is implied; Register)
            """,
            expected = Observe(Register)
        )
    }

    @Test
    fun `Decode Observe Option with value of Deregister`() {
        testReadOption(
            encoded = """
                61 # Option Delta: 6, Option Length: 1
                01 # Option Value: 1 (Deregister)
            """,
            expected = Observe(Deregister)
        )
    }

    @Test
    fun `Decoding Tcp message does not read beyond length specified in header`() {
        val message = Message.Tcp(
            code = GET,
            token = 0,
            options = emptyList(),
            payload = byteArrayOf(0x01, 0x02, 0x03)
        )
        val extraData = byteArrayOf(0x0A, 0x0B, 0x0C)

        assertEquals(
            expected = message,
            actual = (message.encode() + extraData).decode()
        )
    }

    @Test
    fun `Can decode token of length 8`() {
        val message = Message.Tcp(
            code = GET,
            token = Long.MAX_VALUE,
            options = emptyList(),
            payload = "Hello TCP!".toByteArray()
        )

        assertEquals(
            expected = message,
            actual = message.encode().decode()
        )
    }
}

private fun testReadOption(
    encoded: String,
    expected: Message.Option
) {
    val option = encoded
        .stripComments()
        .decodeHex()
        .toByteArray()
        .reader()
        .readOption(preceding = null)

    assertEquals(
        expected = expected,
        actual = option
    )
}

private fun String.stripComments() =
    splitToSequence('\n')
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotBlank() }
        .joinToString("")
        .replace(" ", "")
