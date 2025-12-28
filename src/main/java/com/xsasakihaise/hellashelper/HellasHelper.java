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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static final Logger LOGGER = LogManager.getLogger("HellasHelper");
    private static final String ENTITLEMENT_KEY = MOD_ID;
    private static volatile boolean ENABLED = false;
    private static volatile String DISABLE_REASON = "UNINITIALIZED";

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
        event.enqueueWork(this::initGate);
    }

    private void initGate() {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            ENABLED = true;
            DISABLE_REASON = "OK (non-dedicated)";
            return;
        }

        if (!ModList.get().isLoaded("hellascontrol")) {
            ENABLED = false;
            DISABLE_REASON = "HellasControl missing";
            LOGGER.warn("[HellasHelper] disabled: {}", DISABLE_REASON);
            return;
        }

        try {
            CoreCheck.verifyCoreLoaded();
            CoreCheck.verifyEntitled(ENTITLEMENT_KEY);

            ENABLED = true;
            DISABLE_REASON = "OK";
            LOGGER.info("[HellasHelper] enabled (license OK) entitlement='{}'", ENTITLEMENT_KEY);
        } catch (Exception e) {
            ENABLED = false;
            DISABLE_REASON = "License invalid";
            LOGGER.warn("[HellasHelper] disabled: {} entitlement='{}'", DISABLE_REASON, ENTITLEMENT_KEY, e);
        }
    }

    /**
     * Handles {@link RegisterCommandsEvent} in order to register the
     * collection of {@code /hellas <mod> ...} informational commands.
     *
     * @param event event provided by Forge during the server command build phase
     */
    private void onRegisterCommands(final RegisterCommandsEvent event) {
        if (!ENABLED) {
            return;
        }
        HellasCommandRegistrar.register(event);
    }
}
