package net.clgd.ccemux.emulation.tror

import org.eclipse.xtend.lib.annotations.Accessors

abstract class TRoRPacket<T> {
	@Accessors(PUBLIC_GETTER, PROTECTED_SETTER) var T data
	
	abstract def String getPacketCode()
	
	override toString() {
		packetCode + ":;" + (data ?: "").toString
	}
	
	def String toString(String metadata) {
		packetCode + ':' + metadata + ';' + (data ?: "").toString
	}
}