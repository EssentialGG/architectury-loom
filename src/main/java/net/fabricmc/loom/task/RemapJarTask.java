/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.task;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.gson.JsonObject;
import dev.architectury.tinyremapper.OutputConsumerPath;
import dev.architectury.tinyremapper.TinyRemapper;
import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.at.io.AccessTransformFormats;
import org.cadixdev.lorenz.MappingSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.MixinRefmapHelper;
import net.fabricmc.loom.build.nesting.IncludedJarFactory;
import net.fabricmc.loom.build.nesting.JarNester;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.task.service.JarManifestService;
import net.fabricmc.loom.task.service.MappingsService;
import net.fabricmc.loom.task.service.TinyRemapperService;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.LfWriter;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.aw2at.Aw2At;
import net.fabricmc.loom.util.service.UnsafeWorkQueueHelper;
import net.fabricmc.lorenztiny.TinyMappingsReader;

public abstract class RemapJarTask extends AbstractRemapJarTask {
	private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

	@InputFiles
	public abstract ConfigurableFileCollection getNestedJars();

	@Input
	public abstract Property<Boolean> getAddNestedDependencies();

	/**
	 * Gets the jar paths to the access wideners that will be converted to ATs for Forge runtime.
	 * If you specify multiple files, they will be merged into one.
	 *
	 * <p>The specified files will be converted and removed from the final jar.
	 *
	 * @return the property containing access widener paths in the final jar
	 */
	@Input
	public abstract SetProperty<String> getAtAccessWideners();

	/**
	 * Configures whether to read mixin configs from jar manifest
	 * if a fabric.mod.json cannot be found.
	 *
	 * <p>This is enabled by default on Forge, but not on other platforms.
	 *
	 * @return the property
	 */
	@Input
	public abstract Property<Boolean> getReadMixinConfigsFromManifest();

	/**
	 * Sets the "accessWidener" property in the fabric.mod.json, if the project is
	 * using access wideners.
	 *
	 * @return the property
	 */
	@Input
	public abstract Property<Boolean> getInjectAccessWidener();

	private Supplier<TinyRemapperService> tinyRemapperService = Suppliers.memoize(() -> TinyRemapperService.getOrCreate(this));

	@Inject
	public RemapJarTask() {
		super();

		getClasspath().from(getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
		getAddNestedDependencies().convention(true).finalizeValueOnRead();
		getReadMixinConfigsFromManifest().convention(LoomGradleExtension.get(getProject()).isForge()).finalizeValueOnRead();
		getInjectAccessWidener().convention(false);

		if (LoomGradleExtension.get(getProject()).supportsInclude()) {
			Configuration includeConfiguration = getProject().getConfigurations().getByName(Constants.Configurations.INCLUDE);
			getNestedJars().from(new IncludedJarFactory(getProject()).getNestedJars(includeConfiguration));
		}

		setupPreparationTask();
	}

	private void setupPreparationTask() {
		PrepareJarRemapTask prepareJarTask = getProject().getTasks().create("prepare" + getName().substring(0, 1).toUpperCase() + getName().substring(1), PrepareJarRemapTask.class, this);

		dependsOn(prepareJarTask);
		mustRunAfter(prepareJarTask);

		getProject().getGradle().allprojects(project -> {
			project.getTasks().configureEach(task -> {
				if (task instanceof PrepareJarRemapTask otherTask) {
					// Ensure that all remap jars run after all prepare tasks
					dependsOn(otherTask);
					mustRunAfter(otherTask);
				}
			});
		});
	}

	@TaskAction
	public void run() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		submitWork(RemapAction.class, params -> {
			if (extension.supportsInclude() && getAddNestedDependencies().get()) {
				params.getNestedJars().from(getNestedJars());
			}

			params.getJarManifestService().set(JarManifestService.get(getProject()));
			params.getTinyRemapperBuildServiceUuid().set(UnsafeWorkQueueHelper.create(getProject(), tinyRemapperService.get()));
			params.getRemapClasspath().from(getClasspath());

			final boolean legacyMixin = extension.getMixin().getUseLegacyMixinAp().get();
			params.getUseMixinExtension().set(!legacyMixin);

			if (legacyMixin) {
				setupLegacyMixinRefmapRemapping(params);
			} else if (extension.isForge()) {
				throw new RuntimeException("Forge must have useLegacyMixinAp enabled");
			}

			params.getForge().set(extension.isForge());

			if (getInjectAccessWidener().get() && extension.getAccessWidenerPath().isPresent()) {
				params.getInjectAccessWidener().set(extension.getAccessWidenerPath());
			}

			params.getMappingBuildServiceUuid().convention("this should be unavailable!");
			params.getAtAccessWideners().set(getAtAccessWideners());

			if (!getAtAccessWideners().get().isEmpty()) {
				params.getMappingBuildServiceUuid().set(UnsafeWorkQueueHelper.create(getProject(), MappingsService.createDefault(getProject(), getSourceNamespace().get(), getTargetNamespace().get())));
			}
		});
	}

	private void setupLegacyMixinRefmapRemapping(RemapParams params) {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
		final MixinExtension mixinExtension = extension.getMixin();
		final Collection<String> allMixinConfigs;

		final JsonObject fabricModJson = MixinRefmapHelper.readFabricModJson(getInputFile().getAsFile().get());

		if (fabricModJson == null) {
			if (getReadMixinConfigsFromManifest().get()) {
				allMixinConfigs = readMixinConfigsFromManifest();
			} else {
				getProject().getLogger().warn("Could not find fabric.mod.json file in: " + getInputFile().getAsFile().get().getName());
				return;
			}
		} else {
			allMixinConfigs = MixinRefmapHelper.getMixinConfigurationFiles(fabricModJson);
		}

		for (SourceSet sourceSet : mixinExtension.getMixinSourceSets()) {
			MixinExtension.MixinInformationContainer container = Objects.requireNonNull(
					MixinExtension.getMixinInformationContainer(sourceSet)
			);

			String[] rootPaths = sourceSet.getResources().getSrcDirs().stream()
					.map(root -> {
						String rootPath = root.getAbsolutePath().replace("\\", "/");

						if (rootPath.charAt(rootPath.length() - 1) != '/') {
							rootPath += '/';
						}

						return rootPath;
					})
					.toArray(String[]::new);

			final String refmapName = container.refmapNameProvider().get();
			final List<String> mixinConfigs = container.sourceSet().getResources()
					.matching(container.mixinConfigPattern())
					.getFiles()
					.stream()
					.map(file -> {
						String s = file.getAbsolutePath().replace("\\", "/");

						for (String rootPath : rootPaths) {
							if (s.startsWith(rootPath)) {
								s = s.substring(rootPath.length());
							}
						}

						return s;
					})
					.filter(allMixinConfigs::contains)
					.toList();

			params.getMixinData().add(new RemapParams.RefmapData(mixinConfigs, refmapName));
		}
	}

	private Collection<String> readMixinConfigsFromManifest() {
		File inputJar = getInputFile().get().getAsFile();

		try (JarFile jar = new JarFile(inputJar)) {
			@Nullable Manifest manifest = jar.getManifest();

			if (manifest != null) {
				Attributes attributes = manifest.getMainAttributes();
				String mixinConfigs = attributes.getValue(Constants.Forge.MIXIN_CONFIGS_MANIFEST_KEY);

				if (mixinConfigs != null) {
					return Set.of(mixinConfigs.split(","));
				}
			}

			return Set.of();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read mixin configs from input jar", e);
		}
	}

	public interface RemapParams extends AbstractRemapParams {
		ConfigurableFileCollection getNestedJars();
		ConfigurableFileCollection getRemapClasspath();

		Property<Boolean> getForge();

		RegularFileProperty getInjectAccessWidener();

		SetProperty<String> getAtAccessWideners();

		Property<Boolean> getUseMixinExtension();

		record RefmapData(List<String> mixinConfigs, String refmapName) implements Serializable { }
		ListProperty<RefmapData> getMixinData();

		Property<JarManifestService> getJarManifestService();
		Property<String> getTinyRemapperBuildServiceUuid();
		Property<String> getMappingBuildServiceUuid();
	}

	public abstract static class RemapAction extends AbstractRemapAction<RemapParams> {
		private static final Logger LOGGER = LoggerFactory.getLogger(RemapAction.class);

		private final TinyRemapperService tinyRemapperService;
		private TinyRemapper tinyRemapper;

		public RemapAction() {
			this.tinyRemapperService = UnsafeWorkQueueHelper.get(getParameters().getTinyRemapperBuildServiceUuid(), TinyRemapperService.class);
		}

		@Override
		public void execute() {
			try {
				LOGGER.info("Remapping {} to {}", inputFile, outputFile);

				tinyRemapper = tinyRemapperService.getTinyRemapperForRemapping();

				remap();

				if (!injectAccessWidener()) {
					remapAccessWidener();
				}

				addRefmaps();
				addNestedJars();
				convertAwToAt();

				if (!getParameters().getForge().get()) {
					modifyJarManifest();
				}

				rewriteJar();

				LOGGER.debug("Finished remapping {}", inputFile);
			} catch (Exception e) {
				try {
					Files.deleteIfExists(outputFile);
				} catch (IOException ex) {
					LOGGER.error("Failed to delete output file", ex);
				}

				throw new RuntimeException("Failed to remap", e);
			}
		}

		private void remap() throws IOException {
			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputFile).build()) {
				outputConsumer.addNonClassFiles(inputFile);
				tinyRemapper.apply(outputConsumer, tinyRemapperService.getOrCreateTag(inputFile));
			}
		}

		private boolean injectAccessWidener() throws IOException {
			if (!getParameters().getInjectAccessWidener().isPresent()) return false;

			Path path = getParameters().getInjectAccessWidener().getAsFile().get().toPath();

			byte[] remapped = remapAccessWidener(Files.readAllBytes(path));

			ZipUtils.add(outputFile, path.getFileName().toString(), remapped);

			ZipUtils.transformJson(JsonObject.class, outputFile, Map.of("fabric.mod.json", json -> {
				json.addProperty("accessWidener", path.getFileName().toString());
				return json;
			}));

			return true;
		}

		private void remapAccessWidener() throws IOException {
			final AccessWidenerFile accessWidenerFile = AccessWidenerFile.fromModJar(inputFile);

			if (accessWidenerFile == null) {
				return;
			}

			byte[] remapped = remapAccessWidener(accessWidenerFile.content());

			// Finally, replace the output with the remaped aw
			ZipUtils.replace(outputFile, accessWidenerFile.path(), remapped);
		}

		private void convertAwToAt() throws IOException {
			if (!this.getParameters().getAtAccessWideners().isPresent()) {
				return;
			}

			Set<String> atAccessWideners = this.getParameters().getAtAccessWideners().get();

			if (atAccessWideners.isEmpty()) {
				return;
			}

			AccessTransformSet at = AccessTransformSet.create();
			File jar = outputFile.toFile();

			try (FileSystemUtil.Delegate fileSystem = FileSystemUtil.getJarFileSystem(jar, false)) {
				FileSystem fs = fileSystem.get();
				Path atPath = fs.getPath(Constants.Forge.ACCESS_TRANSFORMER_PATH);

				if (Files.exists(atPath)) {
					throw new FileAlreadyExistsException("Jar " + jar + " already contains an access transformer - cannot convert AWs!");
				}

				for (String aw : atAccessWideners) {
					Path awPath = fs.getPath(aw);

					if (Files.notExists(awPath)) {
						throw new NoSuchFileException("Could not find AW '" + aw + "' to convert into AT!");
					}

					try (BufferedReader reader = Files.newBufferedReader(awPath, StandardCharsets.UTF_8)) {
						at.merge(Aw2At.toAccessTransformSet(reader));
					}

					Files.delete(awPath);
				}

				MappingsService service = UnsafeWorkQueueHelper.get(getParameters().getMappingBuildServiceUuid(), MappingsService.class);

				try (TinyMappingsReader reader = new TinyMappingsReader(service.getMemoryMappingTree(), service.getFromNamespace(), service.getToNamespace())) {
					MappingSet mappingSet = reader.read();
					at = at.remap(mappingSet);
				}

				try (Writer writer = new LfWriter(Files.newBufferedWriter(atPath))) {
					AccessTransformFormats.FML.write(writer, at);
				}
			}
		}

		private byte[] remapAccessWidener(byte[] input) {
			int version = AccessWidenerReader.readVersion(input);

			AccessWidenerWriter writer = new AccessWidenerWriter(version);
			AccessWidenerRemapper remapper = new AccessWidenerRemapper(
					writer,
					tinyRemapper.getEnvironment().getRemapper(),
					getParameters().getSourceNamespace().get(),
					getParameters().getTargetNamespace().get()
			);
			AccessWidenerReader reader = new AccessWidenerReader(remapper);
			reader.read(input);

			return writer.write();
		}

		private void addNestedJars() {
			FileCollection nestedJars = getParameters().getNestedJars();

			if (nestedJars.isEmpty()) {
				LOGGER.info("No jars to nest");
				return;
			}

			JarNester.nestJars(nestedJars.getFiles(), outputFile.toFile(), LOGGER);
		}

		private void modifyJarManifest() throws IOException {
			int count = ZipUtils.transform(outputFile, Map.of(MANIFEST_PATH, bytes -> {
				var manifest = new Manifest(new ByteArrayInputStream(bytes));

				getParameters().getJarManifestService().get().apply(manifest);
				manifest.getMainAttributes().putValue("Fabric-Mapping-Namespace", getParameters().getTargetNamespace().get());

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				manifest.write(out);
				return out.toByteArray();
			}));

			Preconditions.checkState(count > 0, "Did not transform any jar manifest");
		}

		private void addRefmaps() throws IOException {
			if (getParameters().getUseMixinExtension().get()) {
				return;
			}

			for (RemapParams.RefmapData refmapData : getParameters().getMixinData().get()) {
				int transformed = ZipUtils.transformJson(JsonObject.class, outputFile, refmapData.mixinConfigs().stream().collect(Collectors.toMap(s -> s, s -> json -> {
					if (!json.has("refmap")) {
						json.addProperty("refmap", refmapData.refmapName());
					}

					return json;
				})));
			}
		}
	}

	@Internal
	public TinyRemapperService getTinyRemapperService() {
		return tinyRemapperService.get();
	}
}
