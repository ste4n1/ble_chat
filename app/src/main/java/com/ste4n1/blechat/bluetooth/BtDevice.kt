package com.ste4n1.blechat.bluetooth

data class BtDevice(
    val address: String,
    val name: String,
    val bonded: Boolean,
    val rssi: Short? = null,
    val connected: Boolean = false
)
