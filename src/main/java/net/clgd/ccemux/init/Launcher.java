package net.clgd.ccemux.init;

import static org.apache.commons.cli.Option.builder;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.squiddev.cctweaks.lua.launch.RewritingLoader;

import dan200.computercraft.ComputerCraft;
import net.clgd.ccemux.OperatingSystem;
import net.clgd.ccemux.config.ConfigOption;
import net.clgd.ccemux.config.parsers.ParseException;
import net.clgd.ccemux.plugins.PluginManager;

public class Launcher {
	private static final Logger log = LoggerFactory.getLogger(Launcher.class);

	private static final Options opts = new Options();

	// initialize cli options
	static {
		opts.addOption(builder("h").longOpt("help").desc("Shows this help information").build());

		opts.addOption(builder("d").longOpt("data-dir")
				.desc("Sets the data directory where plugins, configs, and other data are stored.").hasArg()
				.argName("path").build());

		opts.addOption(builder("r").longOpt("renderer")
				.desc("Sets the renderer to use. Run without a value to list all available renderers.").hasArg()
				.optionalArg(true).argName("renderer").build());

		opts.addOption(builder().longOpt("plugin").desc(
				"Used to load additional plugins not present in the default plugin directory. Value should be a path to a .jar file.")
				.hasArg().argName("file").build());

		opts.addOption(builder().longOpt("cc")
				.desc("Sepcifies a custom CC jar that will be used in place of the one specified by the config file.")
				.hasArg().argName("file").build());
	}

	private static void printHelp() {
		new HelpFormatter().printHelp("ccemux [args]", opts);
	}

	public static void main(String args[]) {
		try (final RewritingLoader loader = new RewritingLoader(
				((URLClassLoader) Launcher.class.getClassLoader()).getURLs()) {
			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				log.trace("Finding class: {}", name);
				return super.findClass(name);
			}
		}) {
			@SuppressWarnings("unchecked")
			final Class<Launcher> klass = (Class<Launcher>) loader.findClass(Launcher.class.getName());

			final Constructor<Launcher> constructor = klass.getDeclaredConstructor(String[].class);
			constructor.setAccessible(true);

			final Method launch = klass.getDeclaredMethod("launch");
			launch.setAccessible(true);
			launch.invoke(constructor.newInstance(new Object[] { args }));
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Failed to setup rewriting classloader - some features may be unavailable");

			new Launcher(args).launch();
		}
	}

	private final CommandLine cli;
	private final Path dataDir;

	private Launcher(String args[]) {
		// parse cli options
		CommandLine _cli = null;
		try {
			_cli = new DefaultParser().parse(opts, args);
		} catch (org.apache.commons.cli.ParseException e) {
			System.err.println(e.getLocalizedMessage());
			printHelp();
			System.exit(1);
		}

		cli = _cli;

		if (cli.hasOption('h')) {
			printHelp();
			System.exit(0);
		}

		log.info("Starting CCEmuX");
		log.debug("ClassLoader in use: {}", this.getClass().getClassLoader().getClass().getName());

		// set data dir
		if (cli.hasOption('d')) {
			dataDir = Paths.get(cli.getOptionValue('d'));
		} else {
			dataDir = OperatingSystem.get().getAppDataDir().resolve("ccemux");
		}
		log.info("Data directory is {}", dataDir.toString());
	}

	private void crashMessage(Throwable e) {
		CrashReport report = new CrashReport(e);
		log.error("Unexpected exception occurred!", e);
		log.error("CCEmuX has crashed!");

		if (!GraphicsEnvironment.isHeadless()) {
			JTextArea textArea = new JTextArea(12, 60);
			textArea.setEditable(false);
			textArea.setText(report.toString());

			JScrollPane scrollPane = new JScrollPane(textArea);
			scrollPane.setMaximumSize(new Dimension(600, 400));

			int result = JOptionPane.showConfirmDialog(null,
					new Object[] { "CCEmuX has crashed!", scrollPane,
							"Would you like to create a bug report on GitHub?" },
					"CCEmuX Crash", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {
				try {
					report.createIssue();
				} catch (URISyntaxException | IOException e1) {
					log.error("Failed to open GitHub to create issue", e1);
				}
			}
		}
	}

	private void setSystemLAF() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			log.warn("Failed to set system look and feel", e);
		}
	}

	private CCEmuXConfig loadConfig() throws ParseException {
		log.debug("Loading config data");

		CCEmuXConfig cfg = new CCEmuXConfig(dataDir);
		cfg.loadConfig();

		log.info("Config loaded");

		Arrays.stream(cfg.getClass().getDeclaredFields()).filter(f -> f.isAnnotationPresent(ConfigOption.class))
				.forEach(f -> {
					f.setAccessible(true);
					try {
						log.trace("-> {} = {}", f.getName(), f.get(cfg));
					} catch (IllegalAccessException | IllegalArgumentException e) {
					}
				});

		return cfg;
	}

	private PluginManager loadPlugins() {
		File pd = dataDir.resolve("plugins").toFile();

		if (pd.isFile())
			pd.delete();
		if (!pd.exists())
			pd.mkdirs();

		HashSet<URL> urls = new HashSet<>();

		for (File f : pd.listFiles()) {
			log.debug("Adding plugin source '{}'", f.getName());
			try {
				urls.add(f.toURI().toURL());
			} catch (MalformedURLException e) {
				log.warn("Failed to add plugin source", e);
			}
		}

		if (cli.hasOption("plugin")) {
			for (String s : cli.getOptionValues("plugin")) {
				File f = Paths.get(s).toFile();
				log.debug("Adding external plugin source '{}'", f.getName());
				try {
					urls.add(f.toURI().toURL());
				} catch (MalformedURLException e) {
					log.warn("Failed to add plugin source '{}'", f.getName());
				}
			}
		}

		return new PluginManager(urls.toArray(new URL[0]), this.getClass().getClassLoader());
	}

	private void launch() {
		try {
			setSystemLAF();

			File dd = dataDir.toFile();
			if (dd.isFile())
				dd.delete();
			if (!dd.exists())
				dd.mkdirs();

			CCEmuXConfig cfg = loadConfig();

			PluginManager pluginMgr = loadPlugins();
			pluginMgr.loaderSetup();

			if (CCLoader.isLoaded()) {
				log.info("CC already present on classpath, skipping runtime loading");
				if (cli.hasOption("cc")) {
					log.warn("'cc' command line option ignored - classpath already contains CC");
				}
			} else {
				File jar;

				if (cli.hasOption("cc")) {
					jar = Paths.get(cli.getOptionValue("cc")).toFile();
				} else {
					jar = cfg.getCCLocal().toFile();

					if (!jar.exists()) {
						try {
							CCLoader.download(cfg.getCCRemote(), jar);
						} catch (IOException e) {
							log.error("Failed to download CC jar", e);
							if (!GraphicsEnvironment.isHeadless()) {
								JOptionPane.showMessageDialog(null, new JLabel(
										"<html>CCEmuX failed to automatically download the ComputerCraft jar.<br />"
												+ "Please check your internet connection, or manually download the jar to the path below.<br />"
												+ "<pre>" + cfg.getCCLocal().toAbsolutePath().toString()
												+ "</pre><br />"
												+ "If issues persist, please open a bug report.</html>"),
										"Failed to download CC jar", JOptionPane.ERROR_MESSAGE);
								return;
							}
						}
					}
				}

				CCLoader.load(jar);
			}

			log.info("Loaded CC version {}", ComputerCraft.getVersion());
			if (!ComputerCraft.getVersion().equals(cfg.getCCRevision())) {
				log.warn("The CC version expected ({}) does not match the actual CC version ({}) - problems may occur!",
						cfg.getCCRevision(), ComputerCraft.getVersion());
			}
		} catch (Throwable e) {
			crashMessage(e);
		}
	}
}
