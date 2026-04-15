/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft.library.processors;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.util.Platform;

/**
 * A processor to upgrade LWJGL2. Primarily to add support for ARM64.
 */
public class LWJGL2UpgradeLibraryProcessor extends LibraryProcessor {
	private static final String LWJGL_GROUP = "org.lwjgl.lwjgl";
	private static final String LWJGL_VERSION = "2.9.4+legacyfabric.15";

	public LWJGL2UpgradeLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		if (context.usesLWJGL3()) {
			// Not using LWJGL2, can never apply this.
			return ApplicationResult.DONT_APPLY;
		}

		if (platform.getArchitecture().isArm() && platform.getArchitecture().is64Bit()) {
			// Original LWJGL2 does not have ARM64 natives
			return ApplicationResult.MUST_APPLY;
		}

		return ApplicationResult.CAN_APPLY;
	}

	@Override
	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		return library -> {
			if (library.is(LWJGL_GROUP)) {
				dependencyConsumer.accept(library.withVersion(LWJGL_VERSION));
				return false;
			}

			return true;
		};
	}

	@Override
	public void applyRepositories(RepositoryHandler repositories) {
		legacyFabricLWJGL(repositories);
	}

	public static void legacyFabricLWJGL(RepositoryHandler repositories) {
		if (repositories.findByName("LegacyFabricLWJGL") != null) {
			// Already applied.
			return;
		}

		MavenArtifactRepository legacyFabric = repositories.maven(repo -> {
			repo.setName("LegacyFabricLWJGL");
			repo.setUrl("https://maven.legacyfabric.net/");
			repo.content(content -> {
				content.includeVersionByRegex(Pattern.quote(LWJGL_GROUP), ".+", Pattern.quote(LWJGL_VERSION));
			});
		});

		repositories.exclusiveContent(repository -> {
			repository.forRepositories(legacyFabric);
			repository.filter(filter -> {
				filter.includeVersionByRegex(Pattern.quote(LWJGL_GROUP), ".+", Pattern.quote(LWJGL_VERSION));
			});
		});
	}
}
