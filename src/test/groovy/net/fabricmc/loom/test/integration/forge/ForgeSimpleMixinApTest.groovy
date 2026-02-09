/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.test.integration.forge

import java.util.jar.Manifest

import com.google.gson.Gson
import com.google.gson.JsonObject
import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.ZipUtils

import static net.fabricmc.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ForgeSimpleMixinApTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "forge/simpleMixinAp", version: version)

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		// verify the refmap is correctly generated
		def refmap = gradle.getOutputZipEntry("fabric-example-mod-1.0.0.jar", "fabric-example-mod-refmap.json")
		refmap == expected(gradle)
		// verify that the refmap is in the mixin json
		def mixinJsonString = gradle.getOutputZipEntry("fabric-example-mod-1.0.0.jar", "my_mixins.json")
		def mixinJson = new Gson().fromJson(mixinJsonString, JsonObject)
		mixinJson.get("refmap").getAsString() == "fabric-example-mod-refmap.json"
		// verify that the jar manifest has the mixin config
		def main = gradle.getOutputFile("fabric-example-mod-1.0.0.jar").toPath()
		def manifest = new Manifest(new ByteArrayInputStream(ZipUtils.unpack(main, "META-INF/MANIFEST.MF")))
		manifest.getMainAttributes().getValue(Constants.Forge.MIXIN_CONFIGS_MANIFEST_KEY) == "my_mixins.json"

		where:
		version << STANDARD_TEST_VERSIONS
	}

	private String expected(GradleProject gradle) {
		new File(gradle.projectDir, "expected_refmap.json").text.replaceAll('\r', '')
	}
}
