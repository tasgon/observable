package observable.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

open class ResourceKeySerializer<T : Any>(val registryKey: ResourceKey<out Registry<T>>) :
    KSerializer<ResourceKey<T>> {
    private val delegate = String.serializer()
    override val descriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): ResourceKey<T> =
        ResourceKey.create(registryKey, Identifier.parse(delegate.deserialize(decoder)))

    override fun serialize(encoder: Encoder, value: ResourceKey<T>) =
        delegate.serialize(encoder, value.identifier().toString())

    class Dimension : ResourceKeySerializer<Level>(Registries.DIMENSION)
}
