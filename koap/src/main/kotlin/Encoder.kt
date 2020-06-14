package com.juul.koap

import com.juul.koap.Message.Code.Method
import com.juul.koap.Message.Code.Method.DELETE
import com.juul.koap.Message.Code.Method.GET
import com.juul.koap.Message.Code.Method.POST
import com.juul.koap.Message.Code.Method.PUT
import com.juul.koap.Message.Code.Raw
import com.juul.koap.Message.Code.Response
import com.juul.koap.Message.Code.Response.BadGateway
import com.juul.koap.Message.Code.Response.BadOption
import com.juul.koap.Message.Code.Response.BadRequest
import com.juul.koap.Message.Code.Response.Changed
import com.juul.koap.Message.Code.Response.Content
import com.juul.koap.Message.Code.Response.Created
import com.juul.koap.Message.Code.Response.Deleted
import com.juul.koap.Message.Code.Response.Forbidden
import com.juul.koap.Message.Code.Response.GatewayTimeout
import com.juul.koap.Message.Code.Response.InternalServerError
import com.juul.koap.Message.Code.Response.MethodNotAllowed
import com.juul.koap.Message.Code.Response.NotAcceptable
import com.juul.koap.Message.Code.Response.NotFound
import com.juul.koap.Message.Code.Response.NotImplemented
import com.juul.koap.Message.Code.Response.PreconditionFailed
import com.juul.koap.Message.Code.Response.ProxyingNotSupported
import com.juul.koap.Message.Code.Response.RequestEntityTooLarge
import com.juul.koap.Message.Code.Response.ServiceUnavailable
import com.juul.koap.Message.Code.Response.Unauthorized
import com.juul.koap.Message.Code.Response.UnsupportedContentFormat
import com.juul.koap.Message.Code.Response.Valid
import com.juul.koap.Message.Option
import com.juul.koap.Message.Option.Format
import com.juul.koap.Message.Option.Format.empty
import com.juul.koap.Message.Option.Format.opaque
import com.juul.koap.Message.Option.Format.string
import com.juul.koap.Message.Option.Format.uint
import com.juul.koap.Message.Tcp
import com.juul.koap.Message.Udp
import com.juul.koap.Message.Udp.Type.Acknowledgement
import com.juul.koap.Message.Udp.Type.Confirmable
import com.juul.koap.Message.Udp.Type.NonConfirmable
import com.juul.koap.Message.Udp.Type.Reset
import okio.Buffer
import okio.BufferedSink

/**
 * Encodes [Message] receiver as a [ByteArray].
 *
 * [Figure 7: Message Format](https://tools.ietf.org/html/rfc7252#section-3) used for [Message.Udp]:
 *
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |Ver| T |  TKL  |      Code     |          Message ID           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   Token (if any, TKL bytes) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   Options (if any) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |1 1 1 1 1 1 1 1|    Payload (if any) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 *
 * [Figure 4: CoAP Frame for Reliable Transports](https://tools.ietf.org/html/rfc8323#section-3.2)
 * used for [Message.Tcp]:
 *
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Len  |  TKL  | Extended Length (if any, as chosen by Len) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      Code     | Token (if any, TKL bytes) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Options (if any) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |1 1 1 1 1 1 1 1|    Payload (if any) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 */
fun Message.encode(): ByteArray = Buffer().apply { writeMessage(this@encode) }.readByteArray()

private fun BufferedSink.writeMessage(message: Message) {
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |   Token (if any, TKL bytes) ...
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |   Options (if any) ...
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |1 1 1 1 1 1 1 1|    Payload (if any) ...
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    // Content is encoded first, as the encoded content length is needed for `Len` (in TCP header).
    val content = Buffer()
    val tokenLength = message.token?.let { content.writeToken(it) } ?: 0 // Write Token (if present).
    content.apply {
        writeOptions(message.options)
        if (message.payload.isNotEmpty()) {
            writeByte(PAYLOAD_MARKER)
            write(message.payload)
        }
    }

    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |  Len  |  TKL  | Extended Length (if any, as chosen by Len) ...
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    val header = Buffer().apply {
        when (message) {
            is Udp -> writeHeader(message, tokenLength)
            is Tcp -> writeHeader(message, tokenLength, content.size)
        }
    }

    writeAll(header)
    writeAll(content)
}

/**
 * Writes the header portion of
 * [Figure 7: Message Format](https://tools.ietf.org/html/rfc7252#section-3):
 *
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |Ver| T |  TKL  |      Code     |          Message ID           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 *
 * @param message to write the header for
 */
internal fun BufferedSink.writeHeader(message: Udp, tokenLength: Long) {
    require(tokenLength in UINT4_RANGE) {
        "Token length of $tokenLength is outside allowable range of $UINT4_RANGE"
    }

    // |7 6 5 4 3 2 1 0|
    // +-+-+-+-+-+-+-+-+
    // |Ver| T |  TKL  |
    // +-+-+-+-+-+-+-+-+
    val ver = COAP_VERSION shl 6
    val t = message.type.toInt() shl 4
    val tkl = tokenLength.toInt()
    writeByte(ver or t or tkl)

    // |7 6 5 4 3 2 1 0|
    // +-+-+-+-+-+-+-+-+
    // |      Code     |
    // +-+-+-+-+-+-+-+-+
    writeByte(message.code.toInt())

    // |7 6 5 4 3 2 1 0|7 6 5 4 3 2 1 0|
    // |-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |          Message ID           |
    // |-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    writeShort(message.id)
}

// Type (T): 2-bit unsigned integer
// https://tools.ietf.org/html/rfc7252#section-3
private fun Udp.Type.toInt(): Int = when (this) {
    Confirmable -> 0
    NonConfirmable -> 1
    Acknowledgement -> 2
    Reset -> 3
}

/**
 * Writes the header portion of the
 * [Figure 4: CoAP Frame for Reliable Transports](https://tools.ietf.org/html/rfc8323#section-3.2):
 *
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Len  |  TKL  | Extended Length (if any, as chosen by Len) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      Code     |
 * +-+-+-+-+-+-+-+-+
 * ```
 *
 * @param message to write the header for
 * @param contentLength content length, used for calculating `Len`
 */
internal fun BufferedSink.writeHeader(
    message: Tcp,
    tokenLength: Long,
    contentLength: Long
) {
    require(tokenLength in UINT4_RANGE) {
        "Token length of $tokenLength is outside allowable range of $UINT4_RANGE"
    }
    require(contentLength <= UINT32_MAX_EXTENDED_LENGTH) {
        "Content length of $contentLength exceeds maximum allowable of $UINT32_MAX_EXTENDED_LENGTH"
    }

    val len = when {
        contentLength < 13 -> contentLength // No Extended Length
        contentLength < 269 -> 13           // Reserved, indicates  8-bit unsigned integer Extended Length
        contentLength < 65805 -> 14         // Reserved, indicates 16-bit unsigned integer Extended Length
        else -> 15                          // Reserved, indicates 32-bit unsigned integer Extended Length
    }.toInt()
    val tkl = tokenLength.toInt()

    // |7 6 5 4 3 2 1 0|
    // +-+-+-+-+-+-+-+-+
    // |  Len  |  TKL  |
    // +-+-+-+-+-+-+-+-+
    writeByte((len shl 4) or tkl)

    // Extended Length (if any, as chosen by Len) ...
    when (len) {
        // |7 6 5 4 3 2 1 0|
        // +-+-+-+-+-+-+-+-+
        // |  Ext. Length  |
        // +-+-+-+-+-+-+-+-+
        13 -> writeByte(contentLength - 13)   //  8-bit unsigned integer

        // |7 6 5 4 3 2 1 0|7 6 5 4 3 2 1 0|
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // | Extended Length               |
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        14 -> writeShort(contentLength - 269) // 16-bit unsigned integer

        // |7 6 5 4 3 2 1 0|7 6 5 4 3 2 1 0|7 6 5 4 3 2 1 0|7 6 5 4 3 2 1 0|
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // | Extended Length                                               |
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        15 -> writeInt(contentLength - 65805) // 32-bit unsigned integer
    }

    // |7 6 5 4 3 2 1 0|
    // +-+-+-+-+-+-+-+-+
    // |      Code     |
    // +-+-+-+-+-+-+-+-+
    writeByte(message.code.toInt())
}

private fun BufferedSink.writeByte(byte: Long) = writeByte(byte.toInt())
private fun BufferedSink.writeShort(short: Long) = writeShort(short.toInt())
private fun BufferedSink.writeInt(int: Long) = writeInt(int.toInt())

private fun BufferedSink.writeOptions(options: List<Option>) {
    val sorted = options.map(Option::toFormat).sortedBy(Format::number)
    for (i in sorted.indices) {
        val preceding = if (i == 0) null else sorted[i - 1]
        buffer.writeOption(sorted[i], preceding)
    }
}

/**
 * 3.1. Option Format (Figure 8: Option Format)
 *
 * ```
 *   0   1   2   3   4   5   6   7
 * +---------------+---------------+
 * |  Option Delta | Option Length |   1 byte
 * +---------------+---------------+
 * /         Option Delta          /   0-2 bytes
 * \          (extended)           \
 * +-------------------------------+
 * /         Option Length         /   0-2 bytes
 * \          (extended)           \
 * +-------------------------------+
 * \                               \
 * /         Option Value          /   0 or more bytes
 * \                               \
 * +-------------------------------+
 * ```
 */
internal fun BufferedSink.writeOption(option: Format, preceding: Format?) {
    val delta = option.number - (preceding?.number ?: 0)
    val optionDelta = when {
        delta in 0..12 -> delta // No Option Delta (extended)
        delta < 269 -> 13       // Reserved, indicates  8-bit unsigned integer Option Delta (extended)
        delta < 65805 -> 14     // Reserved, indicates 16-bit unsigned integer Option Delta (extended)
        else -> error("Invalid option delta $delta")
    }

    val optionValue = Buffer().apply {
        when (option) {
            is empty -> {
                // no-op
            }
            is opaque -> write(option.value)
            is uint -> {
                var write = false

                // 4 is max length shown for a `uint` in RFC 7252 Table 4: Options
                (4 downTo 0).forEach { i -> // 4 downTo 0 used to write `uint` in network byte-order
                    val byte = (option.value shr (i * Byte.SIZE_BITS)).toInt() and 0xff

                    // Per RFC 7252 3.2, begin writing at first non-zero byte.
                    if (byte != 0) write = true

                    if (write) writeByte(byte)
                }
            }
            is string -> writeUtf8(option.value)
        }
    }

    val length = optionValue.size.toInt()
    val optionLength = when {
        length in 0..12 -> length // No Extended Length
        length < 269 -> 13        // Reserved, indicates  8-bit unsigned integer Option Length (extended)
        length < 65805 -> 14      // Reserved, indicates 16-bit unsigned integer Option Length (extended)
        else -> error("Invalid option length $length")
    }

    //   0   1   2   3   4   5   6   7
    // +---------------+---------------+
    // |  Option Delta | Option Length |   1 byte
    // +---------------+---------------+
    writeByte((optionDelta shl 4) or optionLength)

    // +---------------+---------------+
    // /         Option Delta          /   0-2 bytes
    // \          (extended)           \
    // +-------------------------------+
    when (optionDelta) {
        13 -> writeByte(delta - 13)   //  8-bit unsigned integer
        14 -> writeShort(delta - 269) // 16-bit unsigned integer
    }

    // +-------------------------------+
    // /         Option Length         /   0-2 bytes
    // \          (extended)           \
    // +-------------------------------+
    when (optionLength) {
        13 -> writeByte(length - 13)   //  8-bit unsigned integer
        14 -> writeShort(length - 269) // 16-bit unsigned integer
    }

    // +-------------------------------+
    // \                               \
    // /         Option Value          /   0 or more bytes
    // \                               \
    // +-------------------------------+
    writeAll(optionValue)
}

/**
 * Returns the 8-bit unsigned integer representation of [Message.Code] receiver.
 *
 * Per "RFC 7252 3. Message Format", **Code** is an:
 *
 * > 8-bit unsigned integer, split into a 3-bit class (most significant bits) and a 5-bit detail
 * > (least significant bits), documented as "c.dd" where "c" is a digit from 0 to 7 for the 3-bit
 * > subfield and "dd" are two digits from 00 to 31 for the 5-bit subfield.
 */
private fun Message.Code.toInt(): Int = when (val code = this) {
    // RFC 7252: 12.1.1. Method Codes
    is Method -> when (code) {
        GET -> 1    // 0.01
        POST -> 2   // 0.02
        PUT -> 3    // 0.03
        DELETE -> 4 // 0.04
    }

    // RFC 7252: 12.1.2. Response Codes
    is Response -> when (code) {
        Created -> 65                   // (2 shl 5) or  1  =>  2.01
        Deleted -> 66                   // (2 shl 5) or  2  =>  2.02
        Valid -> 67                     // (2 shl 5) or  3  =>  2.03
        Changed -> 68                   // (2 shl 5) or  4  =>  2.04
        Content -> 69                   // (2 shl 5) or  5  =>  2.05
        BadRequest -> 128               //  4 shl 5         =>  4.00
        Unauthorized -> 129             // (4 shl 5) or  1  =>  4.01
        BadOption -> 130                // (4 shl 5) or  2  =>  4.02
        Forbidden -> 131                // (4 shl 5) or  3  =>  4.03
        NotFound -> 132                 // (4 shl 5) or  4  =>  4.04
        MethodNotAllowed -> 133         // (4 shl 5) or  5  =>  4.05
        NotAcceptable -> 134            // (4 shl 5) or  6  =>  4.06
        PreconditionFailed -> 140       // (4 shl 5) or 12  =>  4.12
        RequestEntityTooLarge -> 141    // (4 shl 5) or 13  =>  4.13
        UnsupportedContentFormat -> 143 // (4 shl 5) or 15  =>  4.15
        InternalServerError -> 160      //  5 shl 5         =>  5.00
        NotImplemented -> 161           // (5 shl 5) or  1  =>  5.01
        BadGateway -> 162               // (5 shl 5) or  2  =>  5.02
        ServiceUnavailable -> 163       // (5 shl 5) or  3  =>  5.03
        GatewayTimeout -> 164           // (5 shl 5) or  4  =>  5.04
        ProxyingNotSupported -> 165     // (5 shl 5) or  5  =>  5.05
    }

    is Raw -> (code.`class` shl 5) or code.detail
}

/**
 * Writes [token] to receiver [BufferedSink].
 *
 * @return length of [token] written.
 */
internal fun BufferedSink.writeToken(token: Long): Long {
    val buffer = Buffer().apply {
        when (token) {
            in UBYTE_RANGE -> writeByte(token and 0xFF)
            in USHORT_RANGE -> writeShort(token and 0xFF_FF)
            in UINT_RANGE -> writeInt(token and 0xFF_FF_FF_FF)
            else -> writeLong(token)
        }
    }
    val size = buffer.size
    write(buffer, size)
    return size
}