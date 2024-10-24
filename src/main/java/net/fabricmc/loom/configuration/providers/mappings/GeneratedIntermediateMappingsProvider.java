package net.fabricmc.loom.configuration.providers.mappings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Stopwatch;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarMerger;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.LoggerFilter;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.stitch.commands.CommandGenerateIntermediary;

public abstract class GeneratedIntermediateMappingsProvider extends IntermediateMappingsProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(GeneratedIntermediateMappingsProvider.class);

	public MinecraftProvider minecraftProvider;

	@Override
	public void provide(Path tinyMappings) throws IOException {
		if (Files.exists(tinyMappings)) {
			return;
		}

		Stopwatch stopwatch = Stopwatch.createStarted();
		LOGGER.info(":generating dummy intermediary");

		// create a temporary folder into which stitch will output the v1 file
		// we cannot just create a temporary file directly, cause stitch will try to read it if it exists
		Path tmpFolder = Files.createTempDirectory("dummy-intermediary");
		Path tinyV1 = tmpFolder.resolve("intermediary-v1.tiny");
		Path mergedJar = tmpFolder.resolve("merged.jar");

		try {
			File clientJar = minecraftProvider.getMinecraftClientJar();
			File serverJar = minecraftProvider.getMinecraftServerJar();

			try (var jarMerger = new MinecraftJarMerger(clientJar, serverJar, mergedJar.toFile())) {
				jarMerger.enableSyntheticParamsOffset();
				jarMerger.merge();
			}

			CommandGenerateIntermediary command = new CommandGenerateIntermediary();
			LoggerFilter.withSystemOutAndErrSuppressed(() -> {
				try {
					command.run(new String[]{ mergedJar.toAbsolutePath().toString(), tinyV1.toAbsolutePath().toString() });
				} catch (IOException e) {
					throw e;
				} catch (Exception e) {
					throw new IOException("Failed to generate intermediary", e);
				}
			});

			try (MappingWriter writer = MappingWriter.create(tinyMappings, MappingFormat.TINY_2_FILE)) {
				MappingReader.read(tinyV1, writer);
			}
		} finally {
			Files.deleteIfExists(mergedJar);
			Files.deleteIfExists(tinyV1);
			Files.delete(tmpFolder);
		}

		LOGGER.info(":generated dummy intermediary in " + stopwatch.stop());
	}

	@Override
	public @NotNull String getName() {
		return "generated-intermediate";
	}
}
