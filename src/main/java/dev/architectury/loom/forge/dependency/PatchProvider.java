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

package dev.architectury.loom.forge.dependency;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import lzma.sdk.lzma.Decoder;
import lzma.sdk.lzma.Encoder;
import lzma.streams.LzmaInputStream;
import lzma.streams.LzmaOutputStream;
import org.gradle.api.Project;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.forge.fg2.Pack200Provider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;

public class PatchProvider extends DependencyProvider {
	private Path projectCacheFolder;
	private Path installerJar;
	private @Nullable Path clientPatches;
	private @Nullable Path serverPatches;

	public PatchProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency) throws Exception {
		init();
		installerJar = getExtension().isModernForgeLike()
				? dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve Forge installer")).toPath()
				: getExtension().getForgeUniversalProvider().getForge().toPath();

		if (getExtension().isLegacyForge()) {
			clientPatches = projectCacheFolder.resolve("patches-client.lzma");
			serverPatches = projectCacheFolder.resolve("patches-server.lzma");
			extractLegacyPatches(clientPatches, serverPatches);
		}
	}

	public Path extractClientPatches() {
		if (clientPatches == null) {
			clientPatches = projectCacheFolder.resolve("patches-client.lzma");
			extractPatches(clientPatches, "client.lzma");
		}

		return clientPatches;
	}

	public Path extractServerPatches() {
		if (serverPatches == null) {
			serverPatches = projectCacheFolder.resolve("patches-server.lzma");
			extractPatches(serverPatches, "server.lzma");
		}

		return serverPatches;
	}

	private void extractPatches(Path targetPath, String name) {
		if (Files.exists(targetPath) && !refreshDeps()) {
			// No need to extract
			return;
		}

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(installerJar, false)) {
			Files.copy(fs.getPath("data", name), targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void init() {
		this.projectCacheFolder = ForgeProvider.getForgeCache(getProject());

		try {
			Files.createDirectories(projectCacheFolder);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void extractLegacyPatches(Path clientPatches, Path serverPatches) throws IOException {
		if (Files.exists(clientPatches) && Files.exists(serverPatches) && !refreshDeps()) {
			// No need to extract
			return;
		}

		byte[] unpackedBytes;

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(installerJar, false)) {
			unpackedBytes = unpack200Lzma(fs.getPath("binpatches.pack.lzma"));
		}

		try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(unpackedBytes));
				OutputStream clientFileOut = Files.newOutputStream(clientPatches, CREATE, TRUNCATE_EXISTING);
				LzmaOutputStream clientLzmaOut = new LzmaOutputStream(clientFileOut, new Encoder());
				JarOutputStream clientJarOut = new JarOutputStream(clientLzmaOut);
				OutputStream serverFileOut = Files.newOutputStream(serverPatches, CREATE, TRUNCATE_EXISTING);
				LzmaOutputStream serverLzmaOut = new LzmaOutputStream(serverFileOut, new Encoder());
				JarOutputStream serverJarOut = new JarOutputStream(serverLzmaOut);
		) {
			for (JarEntry entry; (entry = in.getNextJarEntry()) != null;) {
				String name = entry.getName();

				JarOutputStream out;

				if (name.startsWith("binpatch/client/")) {
					out = clientJarOut;
				} else if (name.startsWith("binpatch/server/")) {
					out = serverJarOut;
				} else {
					getProject().getLogger().warn("Unexpected file in Forge binpatches archive: " + name);
					continue;
				}

				out.putNextEntry(new ZipEntry(name));

				// Converting from legacy format to modern (v1) format
				DataInputStream dataIn = new DataInputStream(in);
				DataOutputStream dataOut = new DataOutputStream(out);
				dataOut.writeByte(1); // version
				dataIn.readUTF(); // unused patch name (presumably always the same as the obf class name)
				dataOut.writeUTF(dataIn.readUTF().replace('.', '/')); // obf class name
				dataOut.writeUTF(dataIn.readUTF().replace('.', '/')); // srg class name
				in.transferTo(out); // remainder is unchanged

				out.closeEntry();
			}
		}
	}

	private byte[] unpack200(InputStream in) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try (JarOutputStream jarOut = new JarOutputStream(bytes)) {
			Pack200Provider provider = getExtension().getForge().getPack200Provider().getOrNull();

			if (provider == null) {
				throw new IllegalStateException("No provider for Pack200 has been found. Did you declare a provider?");
			}

			provider.unpack(in, jarOut);
		}

		return bytes.toByteArray();
	}

	private byte[] unpack200Lzma(InputStream in) throws IOException {
		try (LzmaInputStream lzmaIn = new LzmaInputStream(in, new Decoder())) {
			return unpack200(lzmaIn);
		}
	}

	private byte[] unpack200Lzma(Path path) throws IOException {
		try (InputStream in = Files.newInputStream(path)) {
			return unpack200Lzma(in);
		}
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.FORGE_INSTALLER;
	}
}
