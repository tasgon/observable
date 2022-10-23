package observable.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

open class ResourceKeySerializer<T>(val registryKey: ResourceKey<out Registry<T>>) : KSerializer<ResourceKey<T>> {
    private val delegate = String.serializer()
    override val descriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): ResourceKey<T> =
        ResourceKey.create(registryKey, ResourceLocation(delegate.deserialize(decoder)))

    override fun serialize(encoder: Encoder, value: ResourceKey<T>) =
        delegate.serialize(encoder, value.location().toString())

    class Dimension : ResourceKeySerializer<Level>(Registry.DIMENSION_REGISTRY)
}
