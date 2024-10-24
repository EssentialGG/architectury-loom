package dev.architectury.loom.metadata;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.fabricmc.loom.util.ModPlatform;

public class McmodInfo implements ModMetadataFile {
	public static final String FILE_PATH = "mcmod.info";

	private final JsonArray modsArray;

	private McmodInfo(JsonArray modsArray) {
		this.modsArray = Objects.requireNonNull(modsArray, "modsArray");
	}

	public static McmodInfo of(byte[] utf8) {
		return of(new String(utf8, StandardCharsets.UTF_8));
	}

	public static McmodInfo of(String text) {
		return of(LoomGradlePlugin.GSON.fromJson(text, JsonArray.class));
	}

	public static McmodInfo of(JsonArray jsonArray) {
		return new McmodInfo(jsonArray);
	}

	@Override
	public Set<String> getIds() {
		final ImmutableSet.Builder<String> modIds = ImmutableSet.builder();

		for (final JsonElement mod : modsArray) {
			final String modId = mod.getAsJsonObject().get("modid").getAsString();
			modIds.add(modId);
		}

		return modIds.build();
	}

	@Override
	public Set<String> getAccessWideners() {
		return Set.of();
	}

	@Override
	public Set<String> getAccessTransformers(ModPlatform platform) {
		return Set.of();
	}

	@Override
	public List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
		return List.of();
	}

	@Override
	public String getFileName() {
		return FILE_PATH;
	}

	@Override
	public List<String> getMixinConfigs() {
		return List.of();
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof McmodInfo mcmodInfo && mcmodInfo.modsArray.equals(modsArray);
	}

	@Override
	public int hashCode() {
		return modsArray.hashCode();
	}
}
