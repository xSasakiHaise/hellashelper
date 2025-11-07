package com.xsasakihaise.hellashelper;

import com.xsasakihaise.hellascontrol.api.CoreCheck;
import com.xsasakihaise.hellashelper.command.HellasCommandRegistrar;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Main mod entry point for HellasHelper.
 */
@Mod(HellasHelper.MOD_ID)
public final class HellasHelper {
    public static final String MOD_ID = "hellashelper";

    public HellasHelper() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded("hellascontrol")) {
            return;
        }

        CoreCheck.verifyCoreLoaded();

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            CoreCheck.verifyEntitled(MOD_ID);
        }
    }

    private void onRegisterCommands(final RegisterCommandsEvent event) {
        HellasCommandRegistrar.register(event);
    }
}
