package net.clgd.ccemux.plugins.builtin;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import com.google.auto.service.AutoService;

import net.clgd.ccemux.api.plugins.Plugin;
import net.clgd.ccemux.api.plugins.PluginManager;
import net.clgd.ccemux.rendering.tror.TRoRRenderer;

@AutoService(Plugin.class)
public class TRoRPlugin extends Plugin {
	@Override
	public String getName() {
		return "TRoR Renderer";
	}

	@Override
	public String getDescription() {
		return "A CPU-based renderer which reads from stdin and writes to stdout using the TRoR protocol.";
	}

	@Override
	public Optional<String> getVersion() {
		return Optional.empty();
	}

	@Override
	public Collection<String> getAuthors() {
		return Collections.singleton("CLGD");
	}

	@Override
	public Optional<String> getWebsite() {
		return Optional.empty();
	}

	@Override
	public void setup(PluginManager manager) {
		manager.addRenderer("TRoR", TRoRRenderer::new);
	}
}
