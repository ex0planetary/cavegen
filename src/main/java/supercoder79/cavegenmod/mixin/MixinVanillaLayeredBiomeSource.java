package supercoder79.cavegenmod.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.util.Util;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.BuiltinBiomes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.math.noise.PerlinNoiseSampler;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeLayerSampler;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.gen.SimpleRandom;

import java.util.ArrayList;
import java.util.List;

@Mixin(VanillaLayeredBiomeSource.class)
public class MixinVanillaLayeredBiomeSource {

	@Shadow @Final private BiomeLayerSampler biomeSampler;

	@Shadow @Final private Registry<Biome> biomeRegistry;

	@Unique
	private PerlinNoiseSampler lushCavesNoise;

	@Unique
	private PerlinNoiseSampler dripstoneCavesNoise;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void makeNoise(long seed, boolean legacyBiomeInitLayer, boolean largeBiomes, Registry<Biome> biomeRegistry, CallbackInfo ci) {
		this.lushCavesNoise = new PerlinNoiseSampler(new SimpleRandom(seed));
		this.dripstoneCavesNoise = new PerlinNoiseSampler(new SimpleRandom(seed + 69420));
	}

	private Biome getBiomeFromKey(RegistryKey<Biome> key) {
		Biome biome = (Biome)biomeRegistry.get(key);
		if (biome == null) {
			Util.error("Cave biome surface checker biome ID missing");
			return (Biome)biomeRegistry.get(BuiltinBiomes.fromRawId(0));
		} else {
			return biome;
		}
	}

	private List<Biome> getBiomesFromKeys(List<RegistryKey<Biome>> keys) {
		List<Biome> ret = new ArrayList<Biome>();
		for (RegistryKey<Biome> key : keys) {
			ret.add(getBiomeFromKey(key));
		}
		return ret;
	}

	/**
	 * @author SuperCoder79 (initial code and the bulk of the mixin work)
	 * @author ex0planetary (noise work - 3D, more Mojang-like cave biomes)
	 */
	@Overwrite
	public Biome getBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ) {
		Biome defaultBiome = this.biomeSampler.sample(this.biomeRegistry, biomeX, biomeZ);
		boolean isOceanBiome = defaultBiome.getCategory() == Biome.Category.OCEAN;
		boolean isDeepOceanBiome = isOceanBiome && getBiomesFromKeys(ImmutableList.of(BiomeKeys.DEEP_FROZEN_OCEAN, BiomeKeys.DEEP_COLD_OCEAN, BiomeKeys.DEEP_OCEAN, BiomeKeys.DEEP_LUKEWARM_OCEAN, BiomeKeys.DEEP_WARM_OCEAN)).contains(defaultBiome);
		boolean isHighLushBiome = getBiomesFromKeys(ImmutableList.of(BiomeKeys.FOREST, BiomeKeys.WOODED_HILLS, BiomeKeys.FLOWER_FOREST, BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS)).contains(defaultBiome) || defaultBiome.getCategory() == Biome.Category.JUNGLE;
		boolean isLowLushHighDripBiome = (ImmutableList.of(Biome.Category.EXTREME_HILLS, Biome.Category.MESA, Biome.Category.DESERT, Biome.Category.ICY)).contains(defaultBiome.getCategory());
		int fullyUndergroundY = isOceanBiome ? (isDeepOceanBiome ? 5 : 8) : 11;
		int partiallyUndergroundY = fullyUndergroundY + 2;
		float lushThreshold = isHighLushBiome ? 0.39F : (isLowLushHighDripBiome ? 0.45F : 0.42F);
		float dripThreshold = isLowLushHighDripBiome ? 0.39F : 0.42F;
		double lushFreq = isHighLushBiome ? 50.0 : (isLowLushHighDripBiome ? 80.0 : 65.0);
		double dripFreq = isLowLushHighDripBiome ? 50.0 : 65.0;
		if (biomeY < fullyUndergroundY) {
			// fully underground, no need to worry about gradient shit
			if (this.lushCavesNoise.sample(biomeX / lushFreq, biomeY / 10.0, biomeZ / lushFreq) > lushThreshold) {
				return this.biomeRegistry.get(BiomeKeys.LUSH_CAVES);
			} else if (this.dripstoneCavesNoise.sample(biomeX / dripFreq, biomeY / 15.0, biomeZ / dripFreq) > dripThreshold) {
				return this.biomeRegistry.get(BiomeKeys.DRIPSTONE_CAVES);
			}
		} else if (biomeY < partiallyUndergroundY) {
			// not fully underground - multiply by normalized gradient from 1 at biomeY = 11 to 0 at biomeY = 13 (or other values if it's an ocean lol)
			if (this.lushCavesNoise.sample(biomeX / lushFreq, biomeY / 10.0, biomeZ / lushFreq)*(biomeY - fullyUndergroundY) / 2 > lushThreshold) {
				return this.biomeRegistry.get(BiomeKeys.LUSH_CAVES);
			} else if (this.dripstoneCavesNoise.sample(biomeX / dripFreq, biomeY / 15.0, biomeZ / dripFreq)*(biomeY - fullyUndergroundY) / 2 > dripThreshold) {
				return this.biomeRegistry.get(BiomeKeys.DRIPSTONE_CAVES);
			}
		}

		return defaultBiome;
	}
}
