package dev.orbit.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.util.ApiServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** Lets the in-game account switcher replace the logged-in session and its services. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("apiServices") ApiServices orbit$apiServices();
    @Mutable @Accessor("session") void orbit$setSession(Session s);
    @Mutable @Accessor("userApiService") void orbit$setUserApiService(UserApiService s);
    @Mutable @Accessor("profileKeys") void orbit$setProfileKeys(ProfileKeys k);
    @Mutable @Accessor("socialInteractionsManager") void orbit$setSocialInteractionsManager(SocialInteractionsManager m);
    @Accessor("abuseReportContext") void orbit$setAbuseReportContext(AbuseReportContext c);
    @Mutable @Accessor("gameProfileFuture") void orbit$setGameProfileFuture(CompletableFuture<ProfileResult> f);
    @Accessor("gameProfileFuture") CompletableFuture<ProfileResult> orbit$gameProfileFuture();
}
