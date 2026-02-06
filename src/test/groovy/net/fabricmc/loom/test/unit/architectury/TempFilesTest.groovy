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

package net.fabricmc.loom.test.unit.architectury

import java.nio.file.Files

import dev.architectury.loom.util.TempFiles
import spock.lang.Specification

class TempFilesTest extends Specification {
	TempFiles tempFiles

	def setup() {
		tempFiles = new TempFiles()
	}

	def cleanup() {
		tempFiles.close()
	}

	def "creating temp files"() {
		when:
		def file = tempFiles.file('foo', '.bar')
		then:
		def name = file.fileName.toString()
		name.startsWith('foo')
		name.endsWith('.bar')
		Files.exists(file)
		Files.isRegularFile(file)
	}

	def "deleting temp files"() {
		when:
		def file = tempFiles.file('foo', '.bar')
		tempFiles.close()
		then:
		Files.notExists(file)
	}

	def "creating temp directories"() {
		when:
		def dir = tempFiles.directory('hello world')
		then:
		dir.fileName.toString().startsWith('hello world')
		Files.exists(dir)
		Files.isDirectory(dir)
	}

	def "deleting temp directories"() {
		when:
		def dir = tempFiles.directory('hello')
		Files.writeString(dir.resolve('test.txt'), 'hello world')
		tempFiles.close()
		then:
		Files.notExists(dir)
	}

	def "deleting temp files and directories"() {
		when:
		def dir = tempFiles.directory('hello')
		def file = tempFiles.file('foo', '.bar')
		Files.writeString(dir.resolve('test.txt'), 'hello world')
		Files.writeString(file, 'goodbye world')
		tempFiles.close()
		then:
		Files.notExists(dir)
		Files.notExists(file)
	}
}
