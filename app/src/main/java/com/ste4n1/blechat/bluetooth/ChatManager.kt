package com.ste4n1.blechat.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.ste4n1.blechat.R
import com.ste4n1.blechat.data.ChatRepository
import com.ste4n1.blechat.data.MessageEntity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class ChatManager(
    context: Context,
    private val repository: ChatRepository
) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val prefs = appContext.getSharedPreferences("blechat", Context.MODE_PRIVATE)
    private val myAppId: String = prefs.getString("device_id", null)
        ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString("device_id", id).apply()
        }

    private companion object {
        const val AUTO_SEARCH_MS = 10 * 60 * 1000L
    }
    private val _myName = MutableStateFlow(
        prefs.getString("display_name", null)
            ?: Build.MODEL?.takeIf { it.isNotBlank() }
            ?: "ste4n1"
    )
    val myName: StateFlow<String> = _myName.asStateFlow()
    private val currentName: String get() = _myName.value

    private val _devices = MutableStateFlow<List<BtDevice>>(emptyList())
    val devices: StateFlow<List<BtDevice>> = _devices.asStateFlow()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeers: StateFlow<Set<String>> = _connected.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    private val _transferProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val transferProgress: StateFlow<Map<String, Float>> = _transferProgress.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            _status.value = appContext.getString(
                R.string.bt_transfer_error,
                error.message ?: ""
            )
        }
    )

    private val knownDevices = ConcurrentHashMap<String, BtDevice>()
    private val allConnections = CopyOnWriteArrayList<PeerConnection>()
    private val activeByPeer = ConcurrentHashMap<String, PeerConnection>()
    private val incomingFiles = ConcurrentHashMap<PeerConnection, IncomingFile>()
    private val outgoingJobs = ConcurrentHashMap<String, Job>()
    private val cancelledOutgoing = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var receiverRegistered = false

    @Volatile
    private var serverRunning = false

    private var serverSocket: BluetoothServerSocket? = null

    private val connecting = ConcurrentHashMap.newKeySet<String>()
    private val rescanLock = Any()
    private var rescanJob: Job? = null

    @Volatile
    private var pendingConnect: String? = null

    @Volatile
    private var autoSearchUntil = 0L

    private val peersPrefs by lazy {
        appContext.getSharedPreferences("blechat_peers", Context.MODE_PRIVATE)
    }

    fun setMyName(value: String) {
        val trimmed = value.trim().ifBlank { "ste4n1" }
        prefs.edit().putString("display_name", trimmed).apply()
        _myName.value = trimmed
        ensureAdapterName()
        startDiscovery()
    }

    fun isBluetoothEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (e: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun isDiscoverable(): Boolean = try {
        adapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
    } catch (e: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun ensureStarted() {
        registerReceiverIfNeeded()
        val bt = adapter
        if (bt == null) {
            _status.value = appContext.getString(R.string.bt_not_supported)
            return
        }
        val enabled = try {
            bt.isEnabled
        } catch (e: SecurityException) {
            false
        }
        if (!enabled) {
            _status.value = appContext.getString(R.string.bt_off)
            _discovering.value = false
            return
        }
        ensureAdapterName()
        loadBonded()
        loadKnownPeers()
        startServer()
        startDiscovery()
        autoConnectKnownPeers()
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val bt = adapter ?: return
        autoSearchUntil = System.currentTimeMillis() + AUTO_SEARCH_MS
        try {
            if (bt.isDiscovering) bt.cancelDiscovery()
            val started = bt.startDiscovery()
            _discovering.value = started
            if (!started) _status.value = appContext.getString(R.string.bt_discovery_failed)
        } catch (e: SecurityException) {
            _status.value = appContext.getString(R.string.bt_scan_permission)
        }
    }

    fun connect(address: String) {
        val bt = adapter ?: return
        if (activeByPeer.containsKey(address)) {
            _status.value = appContext.getString(R.string.bt_already_connected)
            return
        }
        val device = try {
            bt.getRemoteDevice(address)
        } catch (e: Exception) {
            _status.value = appContext.getString(R.string.bt_device_not_found)
            return
        }
        autoConnect(address, device, allowPairing = true)
    }

    private fun autoConnect(
        address: String,
        device: BluetoothDevice? = null,
        allowPairing: Boolean = false
    ) {
        if (activeByPeer.containsKey(address)) return
        if (!connecting.add(address)) return
        scope.launch {
            try {
                val target = device ?: try {
                    adapter?.getRemoteDevice(address)
                } catch (e: Exception) {
                    null
                } ?: return@launch
                openSocket(target, allowPairing)
            } finally {
                connecting.remove(address)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openSocket(device: BluetoothDevice, allowPairing: Boolean = false) {
        val address = try {
            device.address
        } catch (e: Exception) {
            return
        }
        if (activeByPeer.containsKey(address)) return
        _status.value = appContext.getString(R.string.bt_connecting, displayName(safeName(device)))
        try {
            adapter?.cancelDiscovery()
        } catch (_: Exception) {
        }

        var socket: BluetoothSocket? = createInsecureSocket(device)
        var connected = socket != null && connectWithTimeout(socket)

        if (!connected) {
            closeQuietly(socket)
            socket = null
            if (safeBondState(device) == BluetoothDevice.BOND_BONDED) {
                socket = createSecureSocket(device)
                connected = socket != null && connectWithTimeout(socket)
            }
        }

        if (!connected) {
            closeQuietly(socket)
            socket = createReflectionSocket(device)
            connected = socket != null && connectWithTimeout(socket)
        }

        val connectedSocket = socket
        if (!connected || connectedSocket == null) {
            closeQuietly(connectedSocket)
            val bonded = safeBondState(device) == BluetoothDevice.BOND_BONDED
            if (allowPairing && !bonded) {
                pendingConnect = address
                _status.value = appContext.getString(R.string.bt_confirm_pairing, displayName(safeName(device)))
                try {
                    device.createBond()
                } catch (e: SecurityException) {
                    pendingConnect = null
                    _status.value = appContext.getString(R.string.bt_pairing_permission)
                }
            } else {
                _status.value = appContext.getString(R.string.bt_connect_failed)
            }
            scheduleRescan()
            return
        }
        // Если обе стороны подключились одновременно, лишнее соединение
        // закроется при выборе победителя после HELLO.
        attachSocket(connectedSocket, outgoing = true)
    }

    private fun connectWithTimeout(
        socket: BluetoothSocket,
        timeoutMs: Long = 8_000L
    ): Boolean {
        val lock = Any()
        var connected = false
        val watchdog = thread(name = "blechat-connect-timeout", isDaemon = true) {
            try {
                Thread.sleep(timeoutMs)
            } catch (_: InterruptedException) {
                return@thread
            }
            synchronized(lock) {
                if (!connected) closeQuietly(socket)
            }
        }
        return try {
            socket.connect()
            synchronized(lock) { connected = true }
            true
        } catch (e: Exception) {
            false
        } finally {
            synchronized(lock) { connected = true }
            watchdog.interrupt()
        }
    }

    private fun createInsecureSocket(device: BluetoothDevice): BluetoothSocket? = try {
        device.createInsecureRfcommSocketToServiceRecord(BtConstants.SERVICE_UUID)
    } catch (e: Exception) {
        null
    }

    private fun createSecureSocket(device: BluetoothDevice): BluetoothSocket? = try {
        device.createRfcommSocketToServiceRecord(BtConstants.SERVICE_UUID)
    } catch (e: Exception) {
        null
    }

    private fun createReflectionSocket(device: BluetoothDevice): BluetoothSocket? {
        val methods = listOf("createInsecureRfcommSocket", "createRfcommSocket")
        for (name in methods) {
            try {
                val method = device.javaClass.getMethod(
                    name,
                    Int::class.javaPrimitiveType
                )
                val socket = method.invoke(device, 1) as BluetoothSocket
                return socket
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun startServer() {
        val bt = adapter ?: return
        if (serverRunning) return
        serverRunning = true
        scope.launch {
            var server: BluetoothServerSocket? = null
            try {
                server = bt.listenUsingInsecureRfcommWithServiceRecord(
                    BtConstants.SERVICE_NAME,
                    BtConstants.SERVICE_UUID
                )
                serverSocket = server
                while (serverRunning) {
                    val socket = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                    if (socket != null) {
                        attachSocket(socket, outgoing = false)
                    }
                }
            } catch (e: SecurityException) {
                _status.value = appContext.getString(R.string.bt_permission_needed)
                serverRunning = false
            } catch (e: Exception) {
                _status.value = appContext.getString(R.string.bt_receive_failed, e.message ?: "")
                serverRunning = false
            } finally {
                serverSocket = null
                serverRunning = false
                try {
                    server?.close()
                } catch (_: Exception) {
                }
                if (adapter?.isEnabled == true) {
                    scope.launch {
                        delay(2_000)
                        if (adapter?.isEnabled == true && !serverRunning) startServer()
                    }
                }
            }
        }
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (e: SecurityException) {
            _status.value = appContext.getString(R.string.bt_permission_needed)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    when (state) {
                        BluetoothAdapter.STATE_ON -> ensureStarted()
                        BluetoothAdapter.STATE_OFF -> {
                            _discovering.value = false
                            _status.value = appContext.getString(R.string.bt_off)
                        }
                    }
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.bluetoothDevice() ?: return
                    val address = try {
                        device.address
                    } catch (e: Exception) {
                        return
                    }
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        ?.takeIf { it.isNotBlank() }
                    val safe = safeName(device)
                    val rawName = when {
                        extraName != null && isAppName(extraName) -> extraName
                        isAppName(safe) -> safe
                        else -> extraName ?: safe
                    }
                    val isKnownPeer = knownPeers().contains(address)
                    // Показываем только устройства, где запущен ble_chat.
                    if (!isAppName(rawName) && !isKnownPeer) return
                    val rssi = intent.getShortExtra(
                        BluetoothDevice.EXTRA_RSSI,
                        Short.MIN_VALUE
                    )
                    addKnownDevice(
                        address,
                        displayName(rawName),
                        safeBondState(device) == BluetoothDevice.BOND_BONDED,
                        if (rssi == Short.MIN_VALUE) null else rssi
                    )
                    autoConnect(address, device)
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _discovering.value = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _discovering.value = false
                    scheduleRescan()
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.bluetoothDevice() ?: return
                    val address = try {
                        device.address
                    } catch (e: Exception) {
                        return
                    }
                    val rawName = safeName(device)
                    if (!isAppName(rawName) && !knownPeers().contains(address)) return
                    val state = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR
                    )
                    when (state) {
                        BluetoothDevice.BOND_BONDED -> {
                            addKnownDevice(address, displayName(rawName), true)
                            if (pendingConnect == address) {
                                pendingConnect = null
                                autoConnect(address, device)
                            }
                        }

                        BluetoothDevice.BOND_NONE -> {
                            addKnownDevice(address, displayName(rawName), false)
                            if (pendingConnect == address) pendingConnect = null
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadBonded() {
        try {
            val remembered = knownPeers()
            adapter?.bondedDevices?.forEach { device ->
                val rawName = safeName(device)
                if (isAppName(rawName) || remembered.contains(device.address)) {
                    addKnownDevice(device.address, displayName(rawName), true)
                }
            }
        } catch (e: SecurityException) {
            _status.value = appContext.getString(R.string.bt_bonded_access)
        }
    }

    private fun loadKnownPeers() {
        for (address in knownPeers()) {
            if (activeByPeer.containsKey(address)) continue
            try {
                val device = adapter?.getRemoteDevice(address) ?: continue
                val rawName = safeName(device)
                addKnownDevice(
                    address,
                    displayName(rawName),
                    safeBondState(device) == BluetoothDevice.BOND_BONDED
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun knownPeers(): Set<String> =
        peersPrefs.getStringSet("peers", emptySet())?.toSet() ?: emptySet()

    private fun rememberPeer(address: String) {
        synchronized(peersPrefs) {
            val peers = knownPeers().toMutableSet()
            if (peers.add(address)) {
                peersPrefs.edit().putStringSet("peers", peers).apply()
            }
        }
    }

    private fun isAppName(name: String): Boolean =
        name.startsWith(BtConstants.NAME_PREFIX, ignoreCase = true)

    private fun displayName(rawName: String): String =
        rawName.removePrefix(BtConstants.NAME_PREFIX)
            .removePrefix(BtConstants.NAME_PREFIX.lowercase())
            .ifBlank { rawName }

    @SuppressLint("MissingPermission")
    private fun ensureAdapterName() {
        val wanted = BtConstants.NAME_PREFIX + currentName.take(20)
        try {
            val bt = adapter ?: return
            if (bt.name == wanted) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bt.setName(wanted)
            } else {
                try {
                    val method = BluetoothAdapter::class.java.getMethod(
                        "setName",
                        String::class.java
                    )
                    method.invoke(bt, wanted)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun autoConnectKnownPeers() {
        scope.launch {
            delay(1_500)
            if (adapter?.isEnabled != true) return@launch
            for (address in knownPeers()) {
                if (!activeByPeer.containsKey(address)) autoConnect(address)
            }
        }
    }

    private fun scheduleRescan() {
        synchronized(rescanLock) {
            if (System.currentTimeMillis() > autoSearchUntil) return
            if (rescanJob?.isActive == true) return
            rescanJob = scope.launch {
                delay(4_000)
                if (System.currentTimeMillis() > autoSearchUntil) return@launch
                if (activeByPeer.isNotEmpty() || connecting.isNotEmpty()) return@launch
                if (adapter?.isEnabled != true) return@launch
                startDiscovery()
            }
        }
    }

    private fun addKnownDevice(
        address: String,
        name: String,
        bonded: Boolean,
        rssi: Short? = null
    ) {
        val existing = knownDevices[address]
        knownDevices[address] = BtDevice(
            address = address,
            name = name.ifBlank { existing?.name ?: address },
            bonded = bonded,
            rssi = rssi ?: existing?.rssi
        )
        publishDevices()
    }

    private fun publishDevices() {
        val active = activeByPeer.keys.toSet()
        _devices.value = knownDevices.values
            .map { it.copy(connected = active.contains(it.address)) }
            .sortedWith(
                compareByDescending<BtDevice> { it.connected }
                    .thenByDescending { it.bonded }
                    .thenBy { it.name.lowercase() }
            )
        _connected.value = active
    }

    private fun attachSocket(socket: BluetoothSocket, outgoing: Boolean) {
        val peerMac = try {
            socket.remoteDevice.address
        } catch (e: Exception) {
            closeQuietly(socket)
            return
        }
        if (pendingConnect == peerMac) pendingConnect = null
        val conn = PeerConnection(socket, peerMac, outgoing)
        allConnections.add(conn)
        conn.start()
        sendHello(conn)
        addKnownDevice(
            peerMac,
            displayName(safePeerName(socket)),
            safeBondState(socket.remoteDevice) == BluetoothDevice.BOND_BONDED
        )
        _status.value = appContext.getString(R.string.bt_connected_to, displayName(safePeerName(socket)))
        publishDevices()
    }

    private fun sendHello(conn: PeerConnection) {
        val json = JSONObject().apply {
            put("deviceId", myAppId)
            put("name", currentName)
        }
        conn.send(BtConstants.TYPE_HELLO, json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun electConnection(peerMac: String) {
        val candidates = allConnections.filter { it.peerMac == peerMac && it.isAlive() }
        if (candidates.isEmpty()) {
            activeByPeer.remove(peerMac)
            publishDevices()
            return
        }
        val peerId = candidates.firstNotNullOfOrNull { it.peerAppId }
        val best = if (peerId != null) {
            val iShouldInitiate = myAppId < peerId
            candidates.firstOrNull { it.outgoing == iShouldInitiate }
                ?: candidates.firstOrNull { it.peerAppId != null }
                ?: candidates.first()
        } else {
            candidates.first()
        }
        activeByPeer[peerMac] = best
        candidates.filter { it !== best }.forEach { it.close() }
        publishDevices()
    }

    private fun onDisconnected(conn: PeerConnection) {
        allConnections.remove(conn)
        cleanupIncoming(conn, deleteFile = true)
        val active = activeByPeer[conn.peerMac]
        if (active === conn) activeByPeer.remove(conn.peerMac)
        electConnection(conn.peerMac)
        if (active === conn && !activeByPeer.containsKey(conn.peerMac)) {
            _status.value = appContext.getString(R.string.bt_disconnected)
        }
        publishDevices()
    }

    private fun handleFrame(conn: PeerConnection, frame: Frame) {
        when (frame.type) {
            BtConstants.TYPE_HELLO -> {
                val json = parseJson(frame) ?: return
                conn.peerAppId = json.optString("deviceId").takeIf { it.isNotBlank() }
                conn.peerName = json.optString("name").takeIf { it.isNotBlank() }
                val name = conn.peerName ?: displayName(safePeerName(conn.socket))
                rememberPeer(conn.peerMac)
                addKnownDevice(
                    conn.peerMac,
                    name,
                    safeBondState(conn.socket.remoteDevice) == BluetoothDevice.BOND_BONDED
                )
                scope.launch { repository.ensureConversation(conn.peerMac, name) }
                electConnection(conn.peerMac)
            }

            BtConstants.TYPE_TEXT -> {
                val json = parseJson(frame) ?: return
                val text = json.optString("text")
                if (text.isBlank() || text.length > BtConstants.MAX_FRAME_SIZE) return
                val msgId = validRemoteId(json.optString("msgId")) ?: UUID.randomUUID().toString()
                val timestamp = safeTimestamp(json.optLong("timestamp", System.currentTimeMillis()))
                val senderName = json.optString("senderName")
                    .ifBlank { conn.peerName ?: conn.peerMac }
                    .take(80)
                val senderId = conn.peerAppId ?: conn.peerMac
                scope.launch {
                    repository.ensureConversation(
                        conn.peerMac,
                        conn.peerName ?: conn.peerMac
                    )
                    repository.insertMessage(
                        MessageEntity(
                            id = msgId,
                            conversationId = conn.peerMac,
                            senderId = senderId,
                            senderName = senderName,
                            isMine = false,
                            type = "TEXT",
                            text = text,
                            filePath = null,
                            mimeType = null,
                            fileSize = null,
                            status = "RECEIVED",
                            timestamp = timestamp
                        )
                    )
                }
            }

            BtConstants.TYPE_FILE_START -> {
                val json = parseJson(frame) ?: return
                val msgId = validRemoteId(json.optString("msgId")) ?: UUID.randomUUID().toString()
                val name = json.optString("name").ifBlank { "file" }.take(120)
                val mime = json.optString("mime").ifBlank { "application/octet-stream" }.take(100)
                val size = json.optLong("size", 0L)
                if (size < 0 || size > BtConstants.MAX_TRANSFER_SIZE) {
                    cleanupIncoming(conn, deleteFile = true)
                    _status.value = appContext.getString(R.string.bt_file_save_failed, "invalid size")
                    return
                }
                val timestamp = safeTimestamp(json.optLong("timestamp", System.currentTimeMillis()))
                val senderName = json.optString("senderName")
                    .ifBlank { conn.peerName ?: conn.peerMac }
                    .take(80)
                cleanupIncoming(conn, deleteFile = true)
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
                    .ifBlank { "file" }
                val dir = File(appContext.filesDir, "received").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}_$safe")
                try {
                    incomingFiles[conn] = IncomingFile(
                        msgId = msgId,
                        name = name,
                        mime = mime,
                        size = size,
                        file = file,
                        out = file.outputStream(),
                        senderName = senderName,
                        timestamp = timestamp
                    )
                } catch (e: Exception) {
                    _status.value = appContext.getString(R.string.bt_file_save_failed, e.message ?: "")
                }
                scope.launch {
                    repository.ensureConversation(conn.peerMac, conn.peerName ?: conn.peerMac)
                }
            }

            BtConstants.TYPE_FILE_CHUNK -> {
                val incoming = incomingFiles[conn] ?: return
                if (incoming.received + frame.payload.size > BtConstants.MAX_TRANSFER_SIZE ||
                    (incoming.size > 0 && incoming.received + frame.payload.size > incoming.size)
                ) {
                    cleanupIncoming(conn, deleteFile = true)
                    _status.value = appContext.getString(R.string.bt_transfer_error, "file too large")
                    return
                }
                try {
                    incoming.out.write(frame.payload)
                    incoming.received += frame.payload.size
                    val progress = if (incoming.size > 0) {
                        (incoming.received.toFloat() / incoming.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _transferProgress.update { it + (incoming.msgId to progress) }
                } catch (e: Exception) {
                    cleanupIncoming(conn, deleteFile = true)
                }
            }

            BtConstants.TYPE_FILE_END -> {
                val incoming = incomingFiles[conn] ?: return
                incomingFiles.remove(conn)
                try {
                    incoming.out.close()
                } catch (_: Exception) {
                }
                if (incoming.size > 0 && incoming.received != incoming.size) {
                    runCatching { incoming.file.delete() }
                    _transferProgress.update { it - incoming.msgId }
                    _status.value = appContext.getString(R.string.bt_transfer_error, "incomplete file")
                    return
                }
                val finalSize = if (incoming.size > 0) incoming.size else incoming.received
                scope.launch {
                    repository.ensureConversation(conn.peerMac, conn.peerName ?: conn.peerMac)
                    repository.insertMessage(
                        MessageEntity(
                            id = incoming.msgId,
                            conversationId = conn.peerMac,
                            senderId = conn.peerAppId ?: conn.peerMac,
                            senderName = incoming.senderName,
                            isMine = false,
                            type = "FILE",
                            text = incoming.name,
                            filePath = incoming.file.absolutePath,
                            mimeType = incoming.mime,
                            fileSize = finalSize,
                            status = "RECEIVED",
                            timestamp = incoming.timestamp
                        )
                    )
                    _transferProgress.update { it - incoming.msgId }
                }
            }

            BtConstants.TYPE_DELETE -> {
                val json = parseJson(frame) ?: return
                val msgId = validRemoteId(json.optString("msgId")) ?: return
                val senderId = conn.peerAppId ?: conn.peerMac
                scope.launch {
                    repository.deleteMessageInConversation(
                        msgId,
                        conn.peerMac,
                        listOf(senderId, conn.peerMac)
                    )
                }
            }
        }
    }

    fun sendText(peerMac: String, text: String) {
        if (text.isBlank()) return
        val job = scope.launch {
            val peerName = knownDevices[peerMac]?.name ?: peerMac
            val msgId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            try {
                repository.ensureConversation(peerMac, peerName)
                repository.insertMessage(
                    MessageEntity(
                        id = msgId,
                        conversationId = peerMac,
                        senderId = myAppId,
                        senderName = currentName,
                        isMine = true,
                        type = "TEXT",
                        text = text,
                        filePath = null,
                        mimeType = null,
                        fileSize = null,
                        status = "SENDING",
                        timestamp = timestamp
                    )
                )
                val conn = activeByPeer[peerMac]
                val payload = JSONObject().apply {
                    put("msgId", msgId)
                    put("text", text)
                    put("timestamp", timestamp)
                    put("senderName", currentName)
                }.toString().toByteArray(Charsets.UTF_8)
                val ok = conn != null && conn.send(BtConstants.TYPE_TEXT, payload)
                repository.updateSendingStatus(
                    msgId,
                    if (cancelledOutgoing.contains(msgId)) "CANCELLED"
                    else if (ok) "SENT" else "FAILED"
                )
            } finally {
                outgoingJobs.remove(msgId)
                cancelledOutgoing.remove(msgId)
            }
        }
        // text is fast; no cancel handle stored by id here
    }

    fun sendFile(peerMac: String, uri: Uri) {
        val msgId = UUID.randomUUID().toString()
        val job = scope.launch {
            try {
                var conn = activeByPeer[peerMac]
                val peerName = knownDevices[peerMac]?.name ?: peerMac
                val resolver = appContext.contentResolver
                var displayName = "file"
                var declaredSize = 0L
                val mime = try {
                    resolver.getType(uri)
                } catch (e: Exception) {
                    null
                } ?: "application/octet-stream"
                try {
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                                displayName = cursor.getString(nameIdx)
                            }
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                declaredSize = cursor.getLong(sizeIdx)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                if (declaredSize > BtConstants.MAX_TRANSFER_SIZE) {
                    _status.value = appContext.getString(R.string.bt_transfer_error, "file too large")
                    return@launch
                }
                val timestamp = System.currentTimeMillis()
                val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
                    .ifBlank { "file" }
                val localFile = File(
                    File(appContext.filesDir, "sent").apply { mkdirs() },
                    "${msgId}_$safe"
                )
                try {
                    resolver.openInputStream(uri)?.use { input ->
                        localFile.outputStream().use { output ->
                            val buffer = ByteArray(BtConstants.CHUNK_SIZE)
                            var copied = 0L
                            while (true) {
                                if (cancelledOutgoing.contains(msgId)) throw IOException("CANCELLED")
                                val read = input.read(buffer)
                                if (read <= 0) break
                                copied += read
                                if (copied > BtConstants.MAX_TRANSFER_SIZE) {
                                    throw IOException("File too large")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: throw IOException("File not found")
                } catch (e: Exception) {
                    _status.value = appContext.getString(R.string.bt_file_read_failed, e.message ?: "")
                    return@launch
                }
                val size = if (localFile.length() > 0) localFile.length() else declaredSize
                repository.ensureConversation(peerMac, peerName)
                repository.insertMessage(
                    MessageEntity(
                        id = msgId,
                        conversationId = peerMac,
                        senderId = myAppId,
                        senderName = currentName,
                        isMine = true,
                        type = "FILE",
                        text = displayName,
                        filePath = localFile.absolutePath,
                        mimeType = mime,
                        fileSize = size,
                        status = "SENDING",
                        timestamp = timestamp
                    )
                )
                val active = activeByPeer[peerMac]
                if (active != null && active.isAlive()) conn = active
                if (conn == null || !conn.isAlive()) {
                    repository.updateSendingStatus(msgId, "FAILED")
                    return@launch
                }
                val start = JSONObject().apply {
                    put("msgId", msgId)
                    put("name", displayName)
                    put("mime", mime)
                    put("size", size)
                    put("timestamp", timestamp)
                    put("senderName", currentName)
                }.toString().toByteArray(Charsets.UTF_8)
                if (!conn.send(BtConstants.TYPE_FILE_START, start)) {
                    repository.updateSendingStatus(msgId, "FAILED")
                    return@launch
                }
                localFile.inputStream().use { input ->
                    val buffer = ByteArray(BtConstants.CHUNK_SIZE)
                    var sent = 0L
                    while (true) {
                        if (cancelledOutgoing.contains(msgId)) throw IOException("CANCELLED")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        if (!conn.send(BtConstants.TYPE_FILE_CHUNK, chunk)) {
                            throw IOException("Соединение прервано")
                        }
                        sent += read
                        val progress = if (size > 0) {
                            (sent.toFloat() / size).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        _transferProgress.update { it + (msgId to progress) }
                    }
                }
                if (cancelledOutgoing.contains(msgId)) throw IOException("CANCELLED")
                conn.send(BtConstants.TYPE_FILE_END, ByteArray(0))
                repository.updateSendingStatus(msgId, "SENT")
            } catch (e: Exception) {
                val cancelled = cancelledOutgoing.contains(msgId) || e.message == "CANCELLED"
                repository.updateSendingStatus(msgId, if (cancelled) "CANCELLED" else "FAILED")
                if (!cancelled) _status.value = appContext.getString(R.string.bt_transfer_error, e.message ?: "")
            } finally {
                _transferProgress.update { it - msgId }
                outgoingJobs.remove(msgId)
                cancelledOutgoing.remove(msgId)
            }
        }
        outgoingJobs[msgId] = job
    }

    fun cancelSend(messageId: String) {
        cancelledOutgoing.add(messageId)
        outgoingJobs[messageId]?.cancel()
        scope.launch { repository.updateSendingStatus(messageId, "CANCELLED") }
    }

    fun deleteMessage(messageId: String) {
        scope.launch { repository.deleteMessage(messageId) }
    }

    fun deleteMessageForEveryone(peerMac: String, messageId: String) {
        scope.launch {
            val peerId = activeByPeer[peerMac]?.peerAppId ?: peerMac
            repository.deleteMessageInConversation(
                messageId,
                peerMac,
                listOf(myAppId, peerId)
            )
            val conn = activeByPeer[peerMac]
            if (conn != null && conn.isAlive()) {
                val payload = JSONObject().apply {
                    put("msgId", messageId)
                }.toString().toByteArray(Charsets.UTF_8)
                conn.send(BtConstants.TYPE_DELETE, payload)
            } else {
                _status.value = appContext.getString(R.string.bt_deleted_local_only)
            }
        }
    }

    private fun validRemoteId(raw: String): String? = raw.takeIf { id ->
        id.length in 1..64 && id.all { c ->
            c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
        }
    }

    private fun safeTimestamp(value: Long): Long {
        val now = System.currentTimeMillis()
        return value.takeIf { it in (now - 365L * 24 * 60 * 60 * 1000)..(now + 24 * 60 * 60 * 1000) }
            ?: now
    }

    private fun cleanupIncoming(conn: PeerConnection, deleteFile: Boolean) {
        val incoming = incomingFiles.remove(conn) ?: return
        try {
            incoming.out.close()
        } catch (_: Exception) {
        }
        if (deleteFile) runCatching { incoming.file.delete() }
        _transferProgress.update { it - incoming.msgId }
    }

    private fun parseJson(frame: Frame): JSONObject? = try {
        JSONObject(String(frame.payload, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    private fun closeQuietly(socket: BluetoothSocket?) {
        if (socket == null) return
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    private fun safeName(device: BluetoothDevice): String = try {
        device.name?.takeIf { it.isNotBlank() } ?: device.address
    } catch (e: SecurityException) {
        try {
            device.address
        } catch (e2: Exception) {
            "Bluetooth"
        }
    }

    private fun safePeerName(socket: BluetoothSocket): String {
        return try {
            val remote = socket.remoteDevice
            try {
                remote.name?.takeIf { it.isNotBlank() } ?: remote.address
            } catch (e: SecurityException) {
                remote.address
            }
        } catch (e: Exception) {
            "Устройство"
        }
    }

    private fun safeBondState(device: BluetoothDevice): Int = try {
        device.bondState
    } catch (e: SecurityException) {
        BluetoothDevice.ERROR
    }

    private class IncomingFile(
        val msgId: String,
        val name: String,
        val mime: String,
        val size: Long,
        val file: File,
        val out: OutputStream,
        val senderName: String,
        val timestamp: Long
    ) {
        var received: Long = 0L
    }

    private inner class PeerConnection(
        val socket: BluetoothSocket,
        val peerMac: String,
        val outgoing: Boolean
    ) {
        @Volatile
        var peerAppId: String? = null

        @Volatile
        var peerName: String? = null

        @Volatile
        private var alive = true

        private var input: InputStream? = null
        private var output: OutputStream? = null
        private var reader: Thread? = null

        fun isAlive(): Boolean = alive

        fun start() {
            try {
                input = socket.inputStream
                output = socket.outputStream
            } catch (e: Exception) {
                close()
                onDisconnected(this)
                return
            }
            reader = thread(name = "blechat-read-$peerMac", isDaemon = true) {
                readLoop()
            }
        }

        fun send(type: Int, payload: ByteArray): Boolean {
            val out = output ?: return false
            return try {
                FrameCodec.write(out, type, payload)
                true
            } catch (e: Exception) {
                close()
                false
            }
        }

        private fun readLoop() {
            val stream = input ?: return
            try {
                while (alive) {
                    val frame = FrameCodec.read(stream) ?: break
                    handleFrame(this, frame)
                }
            } catch (_: Exception) {
            } finally {
                close()
                onDisconnected(this)
            }
        }

        fun close() {
            if (!alive) return
            alive = false
            closeQuietly(socket)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }
}
