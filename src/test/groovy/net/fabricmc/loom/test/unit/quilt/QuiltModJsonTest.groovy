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

package net.fabricmc.loom.test.unit.quilt

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dev.architectury.loom.metadata.QuiltModJson
import spock.lang.Specification
import spock.lang.TempDir

class QuiltModJsonTest extends Specification {
	private static final String OF_TEST_INPUT = '{"access_widener":"foo.accesswidener"}'

	@TempDir
	Path tempDir

	def "create from byte[]"() {
		given:
		def bytes = OF_TEST_INPUT.getBytes(StandardCharsets.UTF_8)
		when:
		def qmj = QuiltModJson.of(bytes)
		then:
		qmj.accessWideners == ['foo.accesswidener'] as Set
	}

	def "create from String"() {
		when:
		def qmj = QuiltModJson.of(OF_TEST_INPUT)
		then:
		qmj.accessWideners == ['foo.accesswidener'] as Set
	}

	def "create from File"() {
		given:
		def file = new File(tempDir.toFile(), 'quilt.mod.json')
		file.text = OF_TEST_INPUT
		when:
		def qmj = QuiltModJson.of(file)
		then:
		qmj.accessWideners == ['foo.accesswidener'] as Set
	}

	def "create from Path"() {
		given:
		def path = tempDir.resolve('quilt.mod.json')
		path.text = OF_TEST_INPUT
		when:
		def qmj = QuiltModJson.of(path)
		then:
		qmj.accessWideners == ['foo.accesswidener'] as Set
	}

	def "create from JsonObject"() {
		given:
		def json = new JsonObject()
		json.addProperty('access_widener', 'foo.accesswidener')
		when:
		def qmj = QuiltModJson.of(json)
		then:
		qmj.accessWideners == ['foo.accesswidener'] as Set
	}

	def "read access widener"() {
		given:
		def qmj = QuiltModJson.of(jsonText)
		when:
		def accessWidenerNames = qmj.accessWideners
		then:
		accessWidenerNames == expectedAw as Set
		where:
		jsonText                                   | expectedAw
		'{}'                                       | []
		'{"access_widener":"foo.accesswidener"}'   | ['foo.accesswidener']
		'{"access_widener":["bar.accesswidener"]}' | ['bar.accesswidener']
		'{"access_widener":["foo.accesswidener","bar.accesswidener"]}' | [
			'foo.accesswidener',
			'bar.accesswidener'
		]
	}

	def "read injected interfaces"() {
		given:
		def qmj = QuiltModJson.of(jsonText)
		when:
		def injectedInterfaces = qmj.getInjectedInterfaces('foo')
		Map<String, List<String>> itfMap = [:]
		for (def entry : injectedInterfaces) {
			itfMap.computeIfAbsent(entry.className()) { [] }.add(entry.ifaceName())
		}
		then:
		itfMap == expected
		where:
		jsonText | expected
		'{}' | [:]
		'{"quilt_loom":{"injected_interfaces":{"target/class/Here":["my/Interface","another/Itf"]}}}' | ['target/class/Here': ['my/Interface', 'another/Itf']]
	}

	def "read mixin configs"() {
		given:
		def qmj = QuiltModJson.of(jsonText)
		when:
		def mixinConfigs = qmj.mixinConfigs
		then:
		mixinConfigs == expected
		where:
		jsonText | expected
		'{}' | []
		'{"mixin":"foo.mixins.json"}' | ['foo.mixins.json']
		'{"mixin":["foo.mixins.json","bar.mixins.json"]}' | [
			'foo.mixins.json',
			'bar.mixins.json'
		]
	}

	def "read mod id"() {
		given:
		def qmj = QuiltModJson.of(jsonText)
		when:
		def id = qmj.id
		then:
		id == expected
		where:
		jsonText | expected
		'{}' | null
		'{"quilt_loader":{"id":"foo"}}' | 'foo'
	}

	def "get file name"() {
		given:
		def qmj = QuiltModJson.of(jsonText)
		when:
		def fileName = qmj.fileName
		then:
		fileName == 'quilt.mod.json'
		where:
		jsonText << [
			'{}',
			'{"quilt_loader":{"id":"foo"}}',
			'{"mixin":"foo.mixins.json"}'
		]
	}

	def "read custom value"() {
		given:
		def qmj = QuiltModJson.of('{"schema_version":1,"quilt_loom":{}}')
		when:
		def customValue = qmj.getCustomValue(key)
		then:
		customValue == expectedValue
		where:
		key              | expectedValue
		'unknown'        | null
		'quilt_loom'     | new JsonObject()
		'schema_version' | new JsonPrimitive(1)
	}
}
