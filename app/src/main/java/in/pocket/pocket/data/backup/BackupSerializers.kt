package `in`.marxen.pocket.data.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeLong(value.toEpochMilli())

    override fun deserialize(decoder: Decoder): Instant =
        Instant.ofEpochMilli(decoder.decodeLong())
}

object YearMonthSerializer : KSerializer<YearMonth> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("YearMonth", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: YearMonth) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): YearMonth =
        YearMonth.parse(decoder.decodeString())
}
