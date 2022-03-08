package net.fabricmc.loom.configuration.processors;

import org.gradle.api.Project;

public class OneCoreProcessor {
	public final String ONECORE_INIT_PATH = "cc.woverflow.onecore.plugin.LoomConnector";
	public final String ONECORE_IDEA_PATH = "cc.woverflow.onecoreidea.plugin.LoomConnector";
	public final String ONECORE_PLUGIN = "cc.woverflow.onecoregradle.plugin.LoomConnector";
	private final String INJECT = "@AetherInject(AetherI, AetherInjector, AetherObject.secure.inject(LoomConnector.VERIFY))";
	public final String VERIFY = "verify.base64.connection.aetherv.10.010(new Protocol(connect-aether.aether.codes)";

	public void OneCoreProcessorProvider(Project project) {

	}
}
