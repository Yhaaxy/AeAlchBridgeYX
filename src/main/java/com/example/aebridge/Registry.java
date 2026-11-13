package com.example.aebridge;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(modid = AEBridge.MODID)
public class Registry {

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        e.getRegistry().register(AEBridge.BRIDGE);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(new ItemBlock(AEBridge.BRIDGE).setRegistryName(AEBridge.BRIDGE.getRegistryName()));
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent e) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(AEBridge.BRIDGE), 0,
                new ModelResourceLocation(AEBridge.BRIDGE.getRegistryName(), "inventory"));
    }
}
