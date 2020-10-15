package com.juul.koap.serialization

import com.juul.koap.Message
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object MessageSerializer : KSerializer<Message> {

    override val descriptor: SerialDescriptor = MessageSurrogate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: Message
    ) {
        val surrogate = when (value) {
            is Message.Udp -> MessageSurrogate.Udp(
                type = value.type,
                code = value.code.toString(),
                id = value.id,
                token = value.token,
                payload = value.payload.hex()
            )
            is Message.Tcp -> MessageSurrogate.Tcp(
                code = value.code.toString(),
                token = value.token,
                payload = value.payload.hex()
            )
        }

        encoder.encodeSerializableValue(
            serializer = MessageSurrogate.serializer(),
            value = surrogate
        )
    }

    override fun deserialize(decoder: Decoder): Message {
        val surrogate = decoder.decodeSerializableValue(MessageSurrogate.serializer())
        TODO()
    }
}

@Serializable
@SerialName("Message")
internal sealed class MessageSurrogate {

    abstract val code: String
    abstract val token: Long

    //    abstract val options: List<Message.Option> // todo
    abstract val payload: String

    @Serializable
    @SerialName("UDP")
    internal data class Udp(
        @SerialName("udpType") val type: Message.Udp.Type,
        override val code: String,
        val id: Int,
        override val token: Long,
//        val options: List<Option> = emptyList(), // todo
        override val payload: String
    ) : MessageSurrogate()

    @Serializable
    @SerialName("TCP")
    internal data class Tcp(
        override val code: String,
        override val token: Long,
//        val options: List<Option> = emptyList(), // todo
        override val payload: String
    ) : MessageSurrogate()
}
