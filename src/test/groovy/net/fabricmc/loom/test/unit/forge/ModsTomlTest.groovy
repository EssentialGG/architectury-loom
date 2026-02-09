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

package net.fabricmc.loom.test.unit.forge

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import com.electronwill.nightconfig.core.io.ParsingException
import dev.architectury.loom.metadata.ModsToml
import spock.lang.Specification
import spock.lang.TempDir

class ModsTomlTest extends Specification {
	private static final String OF_TEST_INPUT =
	'''
            |[[mods]]
            |modId="hello"
            |[[mods]]
            |modId="world"
            '''.stripMargin()
	// codenarc-disable GStringExpressionWithinString
	public static final String BROKEN_INPUT =
	'''
        |[[mods.${MOD_ID}]]
        |modId = "hello_world"
        '''.stripMargin()
	// codenarc-enable GStringExpressionWithinString

	@TempDir
	Path tempDir

	def "create from byte[]"() {
		given:
		def bytes = OF_TEST_INPUT.getBytes(StandardCharsets.UTF_8)
		when:
		def modsToml = ModsToml.of(bytes)
		then:
		modsToml.ids == ['hello', 'world'] as Set
	}

	def "create from String"() {
		when:
		def modsToml = ModsToml.of(OF_TEST_INPUT)
		then:
		modsToml.ids == ['hello', 'world'] as Set
	}

	def "create from File"() {
		given:
		def file = new File(tempDir.toFile(), 'mods.toml')
		file.text = OF_TEST_INPUT
		when:
		def modsToml = ModsToml.of(file)
		then:
		modsToml.ids == ['hello', 'world'] as Set
	}

	def "create from Path"() {
		given:
		def path = tempDir.resolve('mods.toml')
		path.text = OF_TEST_INPUT
		when:
		def modsToml = ModsToml.of(path)
		then:
		modsToml.ids == ['hello', 'world'] as Set
	}

	def "create from invalid string"() {
		when:
		ModsToml.of(BROKEN_INPUT)
		then:
		def e = thrown(IllegalArgumentException)
		e.cause instanceof ParsingException
	}
}
