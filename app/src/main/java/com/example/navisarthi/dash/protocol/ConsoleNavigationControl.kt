package com.example.navisarthi.dash.protocol

internal object ConsoleNavigationControl {
    // OGRE clk.S -> NavigationRootFragment.Z (stop navigation), acknowledged with 06/80/12.
    fun isExit(tlv: Tlv): Boolean = tlv.type == 0x09 && tlv.sub == 0x00 &&
        tlv.value.size == 1 && tlv.value[0] == 0x12.toByte()
}
