# Essential Loom

A fork of [Architectury Loom](https://github.com/architectury/architectury-loom/), primarily to support legacy forge versions
but with some other changes for our purposes. Used by [Essential Gradle Toolkit](https://github.com/EssentialGG/essential-gradle-toolkit/).

Talk to us on [Discord](https://discord.gg/essential). Use the `#programmer-chat` channel for Essential Loom discussions.
Issues can be reported on Discord or using GitHub issues.

# Original Readme

---

# Architectury Loom

Talk to us on [Discord](https://discord.gg/C2RdJDpRBP)!

---

A fork of [Juuxel's Loom fork]("https://github.com/Juuxel/fabric-loom") that is a fork of [Fabric Loom](https://github.com/FabricMC/fabric-loom) that supports the Forge modding toolchain.

A [Gradle](https://gradle.org/) plugin to setup a deobfuscated development environment for Minecraft mods. Primarily used in the Fabric toolchain.

* Has built in support for tiny mappings (Used by [Yarn](https://github.com/FabricMC/yarn))
* Utilises the Fernflower and CFR decompilers to generate source code with comments.
* Designed to support modern versions of Minecraft (Tested with 1.14.4 and upwards)
* ~~Built in support for IntelliJ IDEA, Eclipse and Visual Studio Code to generate run configurations for Minecraft.~~
  - Currently, only IntelliJ IDEA and Visual Studio Code work with Forge Loom.
* Loom targets the latest version of Gradle 7 or newer 
* Supports Java 17 upwards

## Usage

View the [documentation](https://docs.architectury.dev/loom/introduction) for usages.
