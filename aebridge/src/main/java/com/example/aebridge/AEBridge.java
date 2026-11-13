package com.example.aebridge;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid = AEBridge.MODID, name = "ME Alchemy Bridge", version = "1.0.0",
        dependencies = "required-after:appliedenergistics2;after:alchemistry")
public class AEBridge {
    public static final String MODID = "aebridge";

    public static final Block BRIDGE = new BlockBridge();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        GameRegistry.registerTileEntity(TileBridge.class, new ResourceLocation(MODID, "bridge"));
    }
}
