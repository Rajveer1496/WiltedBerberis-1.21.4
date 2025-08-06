package net.fabricmc.example.mixin;

import net.fabricmc.example.WiltedBerberisTracker;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link ClientWorld} to receive particle spawn events and forward them to the tracker.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    // Method signature without the ignoreRange boolean
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V", at = @At("HEAD"))
    private void onAddParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        WiltedBerberisTracker.onParticleSpawn(parameters, x, y, z);
    }

    // Method signature with the two boolean parameters (ignoreRange, alwaysVisible)
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V", at = @At("HEAD"))
    private void onAddParticleRanged(ParticleEffect parameters, boolean ignoreRange, boolean alwaysVisible, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        WiltedBerberisTracker.onParticleSpawn(parameters, x, y, z);
    }
} 