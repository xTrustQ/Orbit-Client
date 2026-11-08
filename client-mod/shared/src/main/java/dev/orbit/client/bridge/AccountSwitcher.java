package dev.orbit.client.bridge;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.report.ReporterEnvironment;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Swaps the logged-in Minecraft account without restarting (use from the title screen). */
public final class AccountSwitcher {
    private AccountSwitcher() {}

    public static UUID uuid(String s) {
        String h = s.replace("-", "");
        return UUID.fromString(h.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    /** Must run on the render thread. */
    public static void apply(String name, UUID id, String token, boolean microsoft) {
        MinecraftClient mc = MinecraftClient.getInstance();
        var acc = (MinecraftClientAccessor) mc;
        Session session = new Session(name, id, token, Optional.empty(), Optional.empty());
        acc.orbit$setSession(session);

        UserApiService api = UserApiService.OFFLINE;
        if (microsoft) {
            try { api = new YggdrasilAuthenticationService(mc.getNetworkProxy()).createUserApiService(token); }
            catch (Exception e) { OrbitClient.LOG.warn("Could not create user API service for {}", name, e); }
        }
        acc.orbit$setUserApiService(api);
        try { acc.orbit$setProfileKeys(ProfileKeys.create(api, session, mc.runDirectory.toPath())); } catch (Exception e) { OrbitClient.LOG.warn("Profile keys", e); }
        try { acc.orbit$setSocialInteractionsManager(new SocialInteractionsManager(mc, api)); } catch (Exception e) { OrbitClient.LOG.warn("Social manager", e); }
        try { acc.orbit$setAbuseReportContext(AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(), api)); } catch (Exception e) { OrbitClient.LOG.warn("Report context", e); }
        var services = acc.orbit$apiServices();
        acc.orbit$setGameProfileFuture(CompletableFuture.supplyAsync(() -> {
            try {
                ProfileResult r = microsoft ? services.sessionService().fetchProfile(id, true) : null;
                return r != null ? r : new ProfileResult(new GameProfile(id, name));
            } catch (Exception e) { return new ProfileResult(new GameProfile(id, name)); }
        }, Util.getIoWorkerExecutor()));
        OrbitClient.LOG.info("Switched account to {}", name);
    }
}
