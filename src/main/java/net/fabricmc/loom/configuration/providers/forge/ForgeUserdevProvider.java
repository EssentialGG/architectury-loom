/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2023 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration.providers.forge;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.architectury.loom.forge.UserdevConfig;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;

import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.mods.dependency.LocalMavenHelper;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ZipUtils;

public class ForgeUserdevProvider extends DependencyProvider {
	private File userdevJar;
	private JsonObject json;
	private UserdevConfig config;
	Path joinedPatches;
	private Integer forgeSpec;

	public ForgeUserdevProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency) throws Exception {
		userdevJar = new File(getExtension().getForgeProvider().getGlobalCache(), "forge-userdev.jar");
		joinedPatches = getExtension().getForgeProvider().getGlobalCache().toPath().resolve("patches-joined.lzma");
		Path configJson = getExtension().getForgeProvider().getGlobalCache().toPath().resolve("forge-config.json");

		if (!userdevJar.exists() || Files.notExists(configJson) || refreshDeps()) {
			File resolved = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve Forge userdev"));
			Files.copy(resolved.toPath(), userdevJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

			byte[] bytes;

			try {
				bytes = ZipUtils.unpack(resolved.toPath(), "config.json");
			} catch (NoSuchFileException e) {
				// If we cannot find a modern config json, try the legacy/FG2-era one
				try {
					bytes = ZipUtils.unpack(resolved.toPath(), "dev.json");
				} catch (NoSuchFileException e1) {
					e.addSuppressed(e1);
					throw e;
				}
			}

			Files.write(configJson, bytes);
		}

		try (Reader reader = Files.newBufferedReader(configJson)) {
			json = new Gson().fromJson(reader, JsonObject.class);
			// Some Forge versions for 1.13.2 specify mcp, but have spec=1. We just "hack" this here.
			forgeSpec = json.has("mcp") ? Math.max(2, json.get("spec").getAsInt()) : 1;

			if (forgeSpec <= 1) {
				json = createManifestFromForgeGradle2(dependency, json);
			} else if (forgeSpec <= 2) {
				addLegacyMCPRepo();
			}

			config = UserdevConfig.CODEC.parse(JsonOps.INSTANCE, json)
					.getOrThrow(false, msg -> getProject().getLogger().error("Couldn't read userdev config, {}", msg));
		}

		addDependency(config.mcp(), Constants.Configurations.MCP_CONFIG);

		if (!getExtension().isNeoForge()) {
			addDependency(config.mcp(), Constants.Configurations.SRG);
		}

		addDependency(config.universal(), Constants.Configurations.FORGE_UNIVERSAL);

		if (forgeSpec >= 2 && Files.notExists(joinedPatches)) {
			Files.write(joinedPatches, ZipUtils.unpack(userdevJar.toPath(), config.binpatches()));
		}
	}

	private JsonObject createManifestFromForgeGradle2(DependencyInfo dependency, JsonObject fg2Json) throws IOException {
		JsonObject json = new JsonObject();

		addLegacyMCPRepo();
		String mcVersion = fg2Json.get("inheritsFrom").getAsString();
		json.addProperty("mcp", "de.oceanlabs.mcp:mcp:" + mcVersion + ":srg@zip");

		json.addProperty("universal", dependency.getDepString() + ":universal");
		json.addProperty("sources", createLegacySources(dependency));
		json.addProperty("patches", "");
		json.addProperty("binpatches", "");
		json.add("binpatcher", createLegacyBinpatcher());
		json.add("libraries", createLegacyLibs(fg2Json));
		json.add("runs", createLegacyRuns());

		return json;
	}

	private static JsonObject createLegacyBinpatcher() {
		JsonObject binpatcher = new JsonObject();
		binpatcher.addProperty("version", "net.minecraftforge:binarypatcher:1.1.1:fatjar");
		JsonArray args = new JsonArray();
		List.of("--clean", "{clean}", "--output", "{output}", "--apply", "{patch}").forEach(args::add);
		binpatcher.add("args", args);
		return binpatcher;
	}

	private static JsonArray createLegacyLibs(JsonObject json) {
		JsonArray array = new JsonArray();

		for (JsonElement lib : json.getAsJsonArray("libraries")) {
			array.add(lib.getAsJsonObject().get("name"));
		}

		return array;
	}

	private static JsonObject createLegacyRuns() {
		JsonObject clientRun = new JsonObject();
		JsonObject serverRun = new JsonObject();
		clientRun.addProperty("name", "client");
		serverRun.addProperty("name", "server");
		clientRun.addProperty("main", Constants.LegacyForge.LAUNCH_WRAPPER);
		serverRun.addProperty("main", Constants.LegacyForge.LAUNCH_WRAPPER);
		JsonArray clientArgs = new JsonArray();
		JsonArray serverArgs = new JsonArray();
		clientArgs.add("--tweakClass");
		serverArgs.add("--tweakClass");
		clientArgs.add(Constants.LegacyForge.FML_TWEAKER);
		serverArgs.add(Constants.LegacyForge.FML_SERVER_TWEAKER);
		clientArgs.add("--accessToken");
		serverArgs.add("--accessToken");
		clientArgs.add("undefined");
		serverArgs.add("undefined");
		clientRun.add("args", clientArgs);
		serverRun.add("args", serverArgs);
		JsonObject runs = new JsonObject();
		runs.add("client", clientRun);
		runs.add("server", serverRun);
		return runs;
	}

	private String createLegacySources(DependencyInfo dependency) throws IOException {
		Path sourceRepo = getExtension().getForgeProvider().getGlobalCache().toPath().resolve("source-repo");
		String group = dependency.getDependency().getGroup();
		String name = dependency.getDependency().getName() + "_sources";
		String version = dependency.getResolvedVersion();
		LocalMavenHelper sourcesMaven = new LocalMavenHelper(group, name, version, "sources", sourceRepo);
		getProject().getRepositories().maven(repo -> {
			repo.setName("LoomFG2Source");
			repo.setUrl(sourceRepo);
		});

		if (!sourcesMaven.exists(null)) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(userdevJar.toPath(), false)) {
				sourcesMaven.copyToMaven(fs.getPath("sources.zip"), null);
			}
		}

		return sourcesMaven.getNotation();
	}

	private void addLegacyMCPRepo() {
		getProject().getRepositories().ivy(repo -> {
			// Old MCP data does not have POMs
			repo.setName("LegacyMCP");
			repo.setUrl("https://maven.minecraftforge.net/");
			repo.patternLayout(layout -> {
				layout.artifact("[orgPath]/[artifact]/[revision]/[artifact]-[revision](-[classifier])(.[ext])");
				// also check the zip so people do not have to explicitly specify the extension for older versions
				layout.artifact("[orgPath]/[artifact]/[revision]/[artifact]-[revision](-[classifier]).zip");
			});
			repo.content(descriptor -> {
				descriptor.includeGroup("de.oceanlabs.mcp");
			});
			repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
		});
	}

	public int getForgeSpec() {
		if (forgeSpec == null) {
			throw new IllegalArgumentException("Not yet resolved.");
		}

		return forgeSpec;
	}

	public File getUserdevJar() {
		return userdevJar;
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.FORGE_USERDEV;
	}

	public JsonObject getJson() {
		return json;
	}

	public UserdevConfig getConfig() {
		return config;
	}
}
