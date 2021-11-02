package wtf.moneymod.client.impl.module.movement;

import club.cafedevelopment.reflectionsettings.annotation.Clamp;
import club.cafedevelopment.reflectionsettings.annotation.Setting;
import org.lwjgl.input.Keyboard;
import wtf.moneymod.client.Main;
import wtf.moneymod.client.impl.module.Module;

@Module.Register( label = "Warp", cat = Module.Category.MOVEMENT, key = Keyboard.KEY_LCONTROL )
public class Warp extends Module {

    //Pro module

    @Setting(id = "Time", clamp = @Clamp(min = 1, max = 16)) int time = 10;
    @Setting(id = "Tick", clamp = @Clamp(min = 1, max = 8)) int tick = 4;
    @Setting(id = "Mode") Mode mode = Mode.TIMER;
    @Setting(id = "Step") boolean step = true;
    //pov when you not pasting ^

    int delay;

    @Override public void onDisable(){
        Main.TICK_TIMER = 1;
    }
    @Override public void onToggle(){
        delay = 0;
        mc.player.stepHeight = 0.6f;
    }
    @Override public void onTick() {
        if (step) mc.player.stepHeight = 2;
        if (mode == Mode.TIMER) {
            delay++;
            Main.TICK_TIMER = tick;
            if (delay >= time) {
                delay = 0;
                setToggled(false);
            }
        }
    }
    public enum Mode {
        SKIP, TIMER
    }
}
