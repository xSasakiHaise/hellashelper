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
 * Entry point for the Hellas Helper sidemod.
 * <p>
 * The Helper mod mostly exposes informational commands that describe the
 * different Hellas suite sidemods. This bootstrap class simply wires the
 * mod into the Forge event system and performs the entitlement checks that
 * are enforced by {@link com.xsasakihaise.hellascontrol.api.CoreCheck}.
 * </p>
 */
@Mod(HellasHelper.MOD_ID)
public final class HellasHelper {
    public static final String MOD_ID = "hellashelper";

    /**
     * Registers the mod lifecycle listeners required by this helper mod.
     * <p>
     * No explicit initialization beyond the entitlement checks and command
     * registration is needed, so the constructor only wires listeners that
     * dispatch to the relevant helper methods in this class.
     * </p>
     */
    public HellasHelper() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    /**
     * Performs common setup on both physical sides and verifies that the
     * Hellas Control core is available before exposing any helper commands.
     *
     * @param event the common setup lifecycle event fired by Forge
     */
    private void onCommonSetup(final FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded("hellascontrol")) {
            return;
        }

        CoreCheck.verifyCoreLoaded();

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            CoreCheck.verifyEntitled(MOD_ID);
        }
    }

    /**
     * Handles {@link RegisterCommandsEvent} in order to register the
     * collection of {@code /hellas <mod> ...} informational commands.
     *
     * @param event event provided by Forge during the server command build phase
     */
    private void onRegisterCommands(final RegisterCommandsEvent event) {
        HellasCommandRegistrar.register(event);
    }
}
