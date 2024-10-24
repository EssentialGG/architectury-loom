/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

import com.google.gson.JsonParseException
import dev.architectury.loom.metadata.McmodInfo
import spock.lang.Specification

class McmodInfoTest extends Specification {
	private static final String TEST_INPUT = '''[{"modid": "hello"}, {"modid": "world"}]'''
	private static final String BROKEN_INPUT = '''{"modid": "hello"}'''

	def "create from byte[]"() {
		given:
		def bytes = TEST_INPUT.getBytes(StandardCharsets.UTF_8)
		when:
		def mcmodInfo = McmodInfo.of(bytes)
		then:
		mcmodInfo.ids == ['hello', 'world'] as Set
	}

	def "create from String"() {
		when:
		def mcmodInfo = McmodInfo.of(TEST_INPUT)
		then:
		mcmodInfo.ids == ['hello', 'world'] as Set
	}

	def "create from invalid string"() {
		when:
		McmodInfo.of(BROKEN_INPUT)
		then:
		thrown(JsonParseException)
	}
}
