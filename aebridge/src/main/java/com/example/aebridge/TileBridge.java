package com.example.aebridge;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.GridAccessException;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.helpers.MachineSource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Узел ME-сети: держит в соседней машине заданный запас предметов
 * и возвращает всё остальное (продукты) в сеть.
 */
public class TileBridge extends TileEntity implements ITickable, IGridProxyable, IActionHost {

    private final AENetworkProxy proxy = new AENetworkProxy(this, "proxy", new ItemStack(AEBridge.BRIDGE), true);
    private final MachineSource source = new MachineSource(this);
    private final List<ItemStack> filters = new ArrayList<>();
    private int tickCounter = 0;

    public TileBridge() {
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        proxy.setIdlePowerUsage(1.0);
    }

    // ---------- Логика ----------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (++tickCounter % 10 != 0) return;
        if (!proxy.isActive()) return;

        try {
            IStorageGrid sg = proxy.getStorage();
            IItemStorageChannel ch = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            IMEMonitor<IAEItemStack> net = sg.getInventory(ch);

            for (EnumFacing f : EnumFacing.values()) {
                TileEntity te = world.getTileEntity(pos.offset(f));
                if (te == null || te instanceof TileBridge) continue;
                EnumFacing side = f.getOpposite();
                if (!te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)) continue;
                IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
                if (handler == null) continue;

                supply(handler, net, ch);
                collect(handler, net, ch);
            }
        } catch (GridAccessException ignored) {
            // сеть недоступна
        }
    }

    /** Докладывает в машину недостающие предметы из ME. */
    private void supply(IItemHandler h, IMEMonitor<IAEItemStack> net, IItemStorageChannel ch) {
        for (ItemStack f : filters) {
            int have = 0;
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (same(s, f)) have += s.getCount();
            }
            int need = f.getCount() - have;
            if (need <= 0) continue;

            IAEItemStack req = ch.createStack(f);
            if (req == null) continue;
            req.setStackSize(need);
            IAEItemStack got = net.extractItems(req, Actionable.MODULATE, source);
            if (got == null || got.getStackSize() <= 0) continue;

            ItemStack rest = ItemHandlerHelper.insertItem(h, got.createItemStack(), false);
            if (!rest.isEmpty()) {
                // не влезло — возвращаем в сеть
                net.injectItems(ch.createStack(rest), Actionable.MODULATE, source);
            }
        }
    }

    /** Забирает из машины то, чего нет в фильтрах, и кладёт в ME. */
    private void collect(IItemHandler h, IMEMonitor<IAEItemStack> net, IItemStorageChannel ch) {
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.extractItem(i, 64, true);
            if (s.isEmpty() || isFiltered(s)) continue;

            IAEItemStack probe = ch.createStack(s);
            if (probe == null) continue;
            IAEItemStack notFit = net.injectItems(probe, Actionable.SIMULATE, source);
            int accept = s.getCount() - (notFit == null ? 0 : (int) notFit.getStackSize());
            if (accept <= 0) continue;

            ItemStack taken = h.extractItem(i, accept, false);
            if (taken.isEmpty()) continue;
            IAEItemStack left = net.injectItems(ch.createStack(taken), Actionable.MODULATE, source);
            if (left != null && left.getStackSize() > 0) {
                ItemHandlerHelper.insertItem(h, left.createItemStack(), false);
            }
        }
    }

    private boolean isFiltered(ItemStack s) {
        for (ItemStack f : filters) if (same(s, f)) return true;
        return false;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return !a.isEmpty() && ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    // ---------- Настройка по ПКМ ----------

    public void handleClick(EntityPlayer player, ItemStack held) {
        if (held.isEmpty()) {
            if (player.isSneaking()) {
                filters.clear();
                markDirty();
                msg(player, "Фильтры очищены");
            } else {
                msg(player, "Фильтров: " + filters.size() + (proxy.isActive() ? " | сеть: онлайн" : " | сеть: офлайн"));
                for (ItemStack f : filters) {
                    player.sendMessage(new TextComponentString(" - " + f.getDisplayName() + " x" + f.getCount()));
                }
            }
            return;
        }
        filters.removeIf(f -> same(f, held));
        if (player.isSneaking()) {
            msg(player, "Убрано: " + held.getDisplayName());
        } else {
            ItemStack f = held.copy();
            f.setCount(Math.min(held.getCount(), 64));
            filters.add(f);
            msg(player, "Держать в машине: " + f.getDisplayName() + " x" + f.getCount());
        }
        markDirty();
    }

    private void msg(EntityPlayer p, String s) {
        p.sendStatusMessage(new TextComponentString(s), true);
    }

    // ---------- NBT ----------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        proxy.writeToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (ItemStack f : filters) list.appendTag(f.writeToNBT(new NBTTagCompound()));
        tag.setTag("filters", list);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        proxy.readFromNBT(tag);
        filters.clear();
        NBTTagList list = tag.getTagList("filters", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack s = new ItemStack(list.getCompoundTagAt(i));
            if (!s.isEmpty()) filters.add(s);
        }
    }

    // ---------- Интеграция с AE2 ----------

    @Override
    public void onLoad() {
        super.onLoad();
        proxy.onReady();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        proxy.invalidate();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        proxy.onChunkUnload();
    }

    @Override
    public AENetworkProxy getProxy() {
        return proxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() { }

    @Override
    public IGridNode getGridNode(AEPartLocation dir) {
        return proxy.getNode();
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {
        world.destroyBlock(pos, true);
    }

    @Override
    public IGridNode getActionableNode() {
        return proxy.getNode();
    }
}
