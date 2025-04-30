package com.milkcocoa.info.crimson.core

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

sealed class ContentConverter<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>(
    val upstreamSerializer: KSerializer<UPSTREAM>,
    val downstreamSerializer: KSerializer<DOWNSTREAM>
) {
    class Nothing<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>: ContentConverter<UPSTREAM, DOWNSTREAM>(
        upstreamSerializer = object: KSerializer<UPSTREAM> {
            override val descriptor = String.serializer().descriptor

            override fun deserialize(decoder: Decoder): UPSTREAM {
                TODO("Not yet implemented")
            }

            override fun serialize(encoder: Encoder, value: UPSTREAM) {
                TODO("Not yet implemented")
            }
        },
        downstreamSerializer = object: KSerializer<DOWNSTREAM> {
            override val descriptor = String.serializer().descriptor

            override fun deserialize(decoder: Decoder): DOWNSTREAM {
                TODO("Not yet implemented")
            }

            override fun serialize(encoder: Encoder, value: DOWNSTREAM) {
                TODO("Not yet implemented")
            }
        },
    )

    sealed class Text<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData> protected constructor(
        private val format: StringFormat,
        upstreamSerializer: KSerializer<UPSTREAM>,
        downstreamSerializer: KSerializer<DOWNSTREAM>
    ): ContentConverter<UPSTREAM, DOWNSTREAM>(upstreamSerializer, downstreamSerializer) {
        suspend fun encodeUpstream(data: UPSTREAM) = format.encodeToString(upstreamSerializer, data)
        suspend fun encodeDownstream(data: DOWNSTREAM) = format.encodeToString(downstreamSerializer, data)
        suspend fun decodeUpstream(data: String) = format.decodeFromString(upstreamSerializer, data)
        suspend fun decodeDownstream(data: String) = format.decodeFromString(downstreamSerializer, data)



        class Json<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData> public constructor(
            json: kotlinx.serialization.json.Json,
            upstreamSerializer: KSerializer<UPSTREAM>,
            downstreamSerializer: KSerializer<DOWNSTREAM>
        ): Text<UPSTREAM, DOWNSTREAM>(
            format = json,
            upstreamSerializer = upstreamSerializer,
            downstreamSerializer = downstreamSerializer
        )

    }

    sealed class Binary<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData> protected constructor(
        private val format: BinaryFormat,
        upstreamSerializer: KSerializer<UPSTREAM>,
        downstreamSerializer: KSerializer<DOWNSTREAM>
    ): ContentConverter<UPSTREAM, DOWNSTREAM>(upstreamSerializer, downstreamSerializer) {
        suspend fun encodeUpstream(data: UPSTREAM) = format.encodeToByteArray(upstreamSerializer, data)
        suspend fun encodeDownstream(data: DOWNSTREAM) = format.encodeToByteArray(downstreamSerializer, data)
        suspend fun decodeUpstream(data: ByteArray) = format.decodeFromByteArray(upstreamSerializer, data)
        suspend fun decodeDownstream(data: ByteArray) = format.decodeFromByteArray(downstreamSerializer, data)


        @OptIn(ExperimentalSerializationApi::class)
        class Protobuf<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>(
            protobuf: kotlinx.serialization.protobuf.ProtoBuf,
            upstreamSerializer: KSerializer<UPSTREAM>,
            downstreamSerializer: KSerializer<DOWNSTREAM>
        ): Binary<UPSTREAM, DOWNSTREAM>(
            format = protobuf,
            upstreamSerializer = upstreamSerializer,
            downstreamSerializer = downstreamSerializer
        )

        @OptIn(ExperimentalSerializationApi::class)
        class Cbor<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>(
            private val cbor: kotlinx.serialization.cbor.Cbor,
            upstreamSerializer: KSerializer<UPSTREAM>,
            downstreamSerializer: KSerializer<DOWNSTREAM>
        ): Binary<UPSTREAM, DOWNSTREAM>(
            format = cbor,
            upstreamSerializer = upstreamSerializer,
            downstreamSerializer = downstreamSerializer
        )
    }

}
