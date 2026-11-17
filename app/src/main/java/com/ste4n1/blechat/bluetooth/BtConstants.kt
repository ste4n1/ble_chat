package com.ste4n1.blechat.bluetooth

import java.util.UUID

object BtConstants {
    val SERVICE_UUID: UUID = UUID.fromString("3f9a6f2c-5b7e-4c1d-9a8f-2e6d4b7c1a55")
    const val SERVICE_NAME = "BleChat"

    // Префикс имени Bluetooth-адаптера. По нему приложение находит только
    // устройства, на которых запущен ble_chat.
    const val NAME_PREFIX = "BChat|"

    const val TYPE_HELLO = 1
    const val TYPE_TEXT = 2
    const val TYPE_FILE_START = 3
    const val TYPE_FILE_CHUNK = 4
    const val TYPE_FILE_END = 5
    const val TYPE_DELETE = 6

    const val CHUNK_SIZE = 16 * 1024
    const val MAX_FRAME_SIZE = 512 * 1024
    const val MAX_TRANSFER_SIZE = 100L * 1024 * 1024
}
