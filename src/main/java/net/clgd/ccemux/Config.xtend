package net.clgd.ccemux

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.ArrayList
import java.util.List
import java.util.Properties
import org.eclipse.xtend.lib.annotations.Accessors

import static extension org.apache.commons.io.IOUtils.copy

class Config extends Properties {
	@Pure
	def private static int parseInt(String str) {
		Integer.parseInt(str)
	}

	public static val CONFIG_FILE_NAME = "ccemux.properties"

	@Accessors(PUBLIC_GETTER) val Path dataDir
	@Accessors(PUBLIC_GETTER) val File configFile

	private val List<Runnable> listeners = new ArrayList();

	new(Path dataDir) {
		// load default properties from embedded resources
		super(new Properties => [
			load(Config.getResourceAsStream("/default.properties"))
			load(Config.getResourceAsStream("/cc.properties"))
		])
		
		this.dataDir = dataDir
		this.configFile = dataDir.resolve(CONFIG_FILE_NAME).toFile

		if (!configFile.exists) {
			new FileOutputStream(configFile) => [
				Config.getResourceAsStream("/default.properties").copy(it)
				flush
				close
			]
		}

		load(new FileInputStream(configFile))
	}

	def synchronized void addListener(Runnable listener) {
		listeners.add(listener)
		listener.run
	}

	override synchronized Object setProperty(String key, String value) {
		val previous = super.setProperty(key, value)
		for(Runnable listener : listeners) listener.run

		return previous
	}

	override synchronized void load(InputStream inStream) throws IOException {
		super.load(inStream)
		for(Runnable listener : listeners) listener.run
	}

	@Pure
	def getTermWidth() {
		getProperty("termWidth").parseInt
	}

	def setTermWidth(int width) {
		setProperty("termWidth", width.toString)
	}

	@Pure
	def getTermHeight() {
		getProperty("termHeight").parseInt
	}

	def setTermHeight(int height) {
		setProperty("termHeight", height.toString)
	}

	@Pure
	def getTermScale() {
		getProperty("termScale").parseInt
	}

	@Pure
	def isApiEnabled() {
		getProperty("apiEnabled") == "true"
	}

	@Pure
	def getRenderer() {
		getProperty("renderer")
	}

	@Pure
	def getCCModule() {
		getProperty("ccModule")
	}

	@Pure
	def getCCRevision() {
		getProperty("ccRevision")
	}

	@Pure
	def getCCExt() {
		getProperty("ccExt")
	}

	@Pure
	def getCCChecksum() {
		getProperty("ccChecksum")
	}

	@Pure
	def getCCPatternRemote() {
		getProperty("ccPatternRemote")
	}

	@Pure
	def getCCPatternLocal() {
		getProperty("ccPatternLocal")
	}

	@Pure
	def getCCRemote() {
		CCPatternRemote.replace("[module]", CCModule).replace("[revision]", CCRevision).replace("[ext]", CCExt)
	}

	@Pure
	def getCCLocal() {
		CCPatternLocal.replace("[module]", CCModule).replace("[revision]", CCRevision).replace("[ext]", CCExt)
	}

	@Pure
	def getCCTweaks() {
		Boolean.parseBoolean(getProperty("cctweaks.enabled"))
	}

	def saveProperties() {
		new FileOutputStream(configFile) => [
			store(it, null)

			flush
			close
		]
	}
}
