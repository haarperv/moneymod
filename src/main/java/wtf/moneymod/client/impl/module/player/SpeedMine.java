package wtf.moneymod.client.impl.module.player;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.Sys;
import wtf.moneymod.client.api.events.DamageBlockEvent;
import wtf.moneymod.client.api.events.MoveEvent;
import wtf.moneymod.client.api.setting.annotatable.Bounds;
import wtf.moneymod.client.api.setting.annotatable.Value;
import wtf.moneymod.client.impl.module.Module;
import wtf.moneymod.client.impl.utility.impl.math.MathUtil;
import wtf.moneymod.client.impl.utility.impl.misc.Timer;
import wtf.moneymod.client.impl.utility.impl.player.ItemUtil;
import wtf.moneymod.client.impl.utility.impl.player.ToolUtil;
import wtf.moneymod.client.impl.utility.impl.render.Renderer3D;
import wtf.moneymod.client.impl.utility.impl.world.BlockUtil;
import wtf.moneymod.client.mixin.accessors.IPlayerControllerMP;
import wtf.moneymod.eventhandler.listener.Handler;
import wtf.moneymod.eventhandler.listener.Listener;

import java.awt.*;

@Module.Register( label = "SpeedMine", cat = Module.Category.PLAYER )
public class SpeedMine extends Module {

    @Value( value = "Renderer" ) public RenderMode mode = RenderMode.FADE;
    @Value( value = "Render" ) public boolean render = true;
    @Value( value = "Silent" ) public boolean silent = true;
    @Value( value = "Instant Rebreak" ) public boolean instant = true;
    @Value( "Strict Rebreak" ) public boolean strict = true;
    @Value( value = "Range" ) @Bounds( min = 4, max = 30 ) public int range = 16;
    @Value( value = "Packet Spam" ) @Bounds( min = 1, max = 10 ) public int spam = 1;
    public Color color = new Color(255, 0, 0, 75);
    public Color readyColor = new Color(0, 255, 0, 75);

    private BlockPos currentPos;
    private final Timer timer = new Timer();
    private long start;
    private int old, delay;
    public boolean swap = false, checked, strictCheck;

    @Override protected void onToggle() {
        old = -1;
        currentPos = null;
        delay = 0;
    }

    @Override public void onTick() {
        //this swap code is sooo trash
        if (swap) {
            mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, currentPos, EnumFacing.DOWN));
            if (delay >= 1) {
                if (old != -1) {
                    ItemUtil.swapToHotbarSlot(old, false);
                }
                swap = false;
                if (!instant) currentPos = null;
                delay = 0;
            }
            delay++;
        }
    }

    @Handler public Listener<MoveEvent> moveEventListener = new Listener<>(MoveEvent.class, e -> {
        if (currentPos != null) {
            if (instant) {
                if (mc.world.getBlockState(currentPos).getBlock() == Blocks.AIR) {
                    if (!checked) {
                        checked = true;
                        start = System.currentTimeMillis();
                        timer.reset();
                        strictCheck = false;
                    }
                } else {
                    if(strict && !strictCheck) {
                        Block block = mc.world.getBlockState(currentPos).getBlock();
                        if(!(block.equals(Blocks.ENDER_CHEST) || block.equals(Blocks.ANVIL) || block.equals(Blocks.AIR))) {
                            currentPos = null;
                            timer.reset();
                            strictCheck = true;
                            return;
                        }
                    }
                    checked = false;
                }

            }

            if (instant && mc.player.inventory.currentItem == ToolUtil.INSTANCE.bestSlot(currentPos) && getBlockProgress(currentPos, mc.player.inventory.getStackInSlot(ToolUtil.INSTANCE.bestSlot(currentPos)), start) <= 0.1 && mc.world.getBlockState(currentPos).getBlock() != Blocks.AIR && ( !swap || delay > 2 ) ) {
                Block block = mc.world.getBlockState(currentPos).getBlock();
                if(strict && !(block.equals(Blocks.ENDER_CHEST) || block.equals(Blocks.ANVIL))) return;
                mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, currentPos, EnumFacing.DOWN));
            }

            if (!swap) old = mc.player.inventory.currentItem;

            if (mc.player.getDistanceSq(currentPos) >= MathUtil.INSTANCE.square(range)) {
                currentPos = null;
            }
        }
        try {
            (( IPlayerControllerMP ) mc.playerController).setBlockHitDelay(0);
        } catch (Exception ignored) {}

    });

    @Handler public Listener<DamageBlockEvent> damageBlockEvent = new Listener<>(DamageBlockEvent.class, e -> {
        if (swap) {
            e.setCancelled(true);
            return;
        }

        if (nullCheck() || !BlockUtil.INSTANCE.canBlockBeBroken(e.getBlockPos())) return;

        if (currentPos != null) {

            if (e.getBlockPos().toLong() == currentPos.toLong() && !swap && getBlockProgress(currentPos, mc.player.inventory.getStackInSlot(ToolUtil.INSTANCE.bestSlot(currentPos)), start) <= 0.1 && mc.world.getBlockState(currentPos).getBlock() != Blocks.AIR) {
                ItemUtil.swapToHotbarSlot(ToolUtil.INSTANCE.bestSlot(currentPos), silent);
                swap = true;
                e.cancel();
                return;
            }

            if(e.getBlockPos().toLong() != currentPos.toLong()) {
                mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, currentPos, EnumFacing.DOWN));
            }

        }

        mc.player.swingArm(EnumHand.MAIN_HAND);
        for (int j = 0; j < spam; j++) {
            mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, e.getBlockPos(), e.getFaceDirection()));
        }
        mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, e.getBlockPos(), EnumFacing.DOWN));
        currentPos = e.getBlockPos();
        start = System.currentTimeMillis();
        strictCheck = true;
        timer.reset();
        e.setCancelled(true);
    });

    @Override public void onRender3D(float partialTicks) {
        if (currentPos == null || !render || mc.world.getBlockState(currentPos).getBlock() == Blocks.AIR || mc.world.getBlockState(currentPos).getBlock() instanceof BlockLiquid)
            return;
        AxisAlignedBB bb = mc.world.getBlockState(currentPos).getSelectedBoundingBox(mc.world, currentPos);
        float progress = getBlockProgress(currentPos, mc.player.inventory.getStackInSlot(ToolUtil.INSTANCE.bestSlot(currentPos)), start);
        if (progress <= 0.1) {
            Renderer3D.drawBoxESP(bb, readyColor, 1f, true, true, readyColor.getAlpha(), 255);
        } else {
            if (mode == RenderMode.FADE) {
                Renderer3D.drawBoxESP(bb, new Color(( int ) (color.getRed() * progress), ( int ) (readyColor.getGreen() * (1 - progress)), color.getBlue()), 1f, true, true, color.getAlpha(), 255);
            } else {
                Renderer3D.INSTANCE.drawProgressBox(bb, progress, color);
            }
        }
    }

    float getBlockProgress(BlockPos blockPos, ItemStack stack, long start) {
        return ( float ) MathUtil.INSTANCE.clamp(1 - ((System.currentTimeMillis() - start) / ( double ) ToolUtil.INSTANCE.time(blockPos, stack)), 0, 1);
    }

    public enum RenderMode {
        FADE,
        EXPAND
    }

}
