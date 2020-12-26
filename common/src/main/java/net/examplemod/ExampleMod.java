package net.examplemod;

import me.shedaniel.architectury.registry.CreativeTabs;
import me.shedaniel.architectury.registry.Registries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.LazyLoadedValue;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ExampleMod {
    public static final String MOD_ID = "examplemod";
    public static final LazyLoadedValue<Registries> REGISTRIES = new LazyLoadedValue<>(() -> Registries.get(MOD_ID));
    // Registering a new creative tab
    public static final CreativeModeTab EXAMPLE_TAB = CreativeTabs.create(new ResourceLocation(MOD_ID, "example_tab"), () -> new ItemStack(Items.DIAMOND_SWORD));
    
    public static void init() {
        // Using the architectury registry to defer the creation of new entries to when they are registered.
        REGISTRIES.get().get(Registry.ITEM_REGISTRY).register(new ResourceLocation(MOD_ID, "example_item"), () ->
                new Item(new Item.Properties().tab(ExampleMod.EXAMPLE_TAB)));
    }
}
