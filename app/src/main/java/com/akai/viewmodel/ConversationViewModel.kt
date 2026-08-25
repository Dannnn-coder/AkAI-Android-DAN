package com.akai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.akai.data.ConversationEntry
import com.akai.data.ConversationRepository
import com.akai.data.SenderType
import com.akai.service.FSLRecognitionService
import com.akai.service.NearbySyncService
import com.akai.service.VoskSTTService
import com.akai.service.WordAssemblyLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which conversation mode the app is in. */
enum class SyncMode { SINGLE_DEVICE, TWO_DEVICE }

/** State of the two-device connection, so the UI can show the right thing. */
enum class ConnectionState { IDLE, HOSTING, JOINING, CONNECTED, ERROR }

class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    // Services
    val fslService = FSLRecognitionService(application)
    val voskService = VoskSTTService(application)
    val wordAssembly = WordAssemblyLayer()
    private val nearbyService = NearbySyncService(application)

    // Repository
    private val repository = ConversationRepository()

    // Observable state
    val entries = MutableLiveData<List<ConversationEntry>>(emptyList())
    val isFSLMode = MutableLiveData<Boolean>(true)
    val isRecording = MutableLiveData<Boolean>(false)
    val modelsReady = MutableLiveData<Boolean>(false)

    // ---- Two-device (offline sync) state ----
    val syncMode = MutableLiveData(SyncMode.SINGLE_DEVICE)
    val connectionState = MutableLiveData(ConnectionState.IDLE)
    /** Current session code (e.g. "AK-4829"), shown in the UI. */
    val sessionCode = MutableLiveData("")
    /** User-safe error text for the two-device flow. */
    val syncError = MutableLiveData<String?>(null)

    init {
        // Load FSL during splash — Vosk loads lazily when user switches to speech mode.
        // modelsReady is set in finally{} so it ALWAYS fires even if loadModel() throws,
        // preventing the splash from being stuck forever on a model load failure.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fslService.loadModel()
            } catch (e: Exception) {
                android.util.Log.e("ConversationViewModel", "FSL model load failed: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    modelsReady.value = true
                }
            }
        }

        wireNearbyCallbacks()
    }

    // =====================================================================
    //  MESSAGE FLOW
    //  addDeafMessage / addHearingMessage are the single chokepoint every
    //  message flows through. We (1) add it locally, then (2) if we're in a
    //  connected two-device session, ship it to the paired phone.
    //  Messages ARRIVING from the peer go through addRemoteEntry(), which
    //  adds them locally WITHOUT re-sending (or they'd bounce forever).
    // =====================================================================

    fun addDeafMessage(text: String) {
        val entry = ConversationEntry(text = text, sender = SenderType.DEAF)
        addAndBroadcast(entry)
    }

    fun addHearingMessage(text: String) {
        val entry = ConversationEntry(text = text, sender = SenderType.HEARING)
        addAndBroadcast(entry)
    }

    /** Add a locally-produced entry to the thread and, if connected, send it to the peer. */
    private fun addAndBroadcast(entry: ConversationEntry) {
        repository.addEntry(entry)
        entries.postValue(repository.getAll())
        if (nearbyService.isConnected()) {
            nearbyService.sendMessage(entry.toJson())
        }
    }

    /**
     * An entry arrived from the paired phone. Add it to our local thread so it
     * shows on this screen too. We do NOT re-broadcast (that would loop), and we
     * skip duplicates by id in case a packet is somehow delivered twice.
     */
    private fun addRemoteEntry(json: String) {
        val entry = ConversationEntry.fromJson(json) ?: return  // ignore malformed packets
        if (repository.getAll().any { it.id == entry.id }) return // dedupe by id
        repository.addEntry(entry)
        entries.postValue(repository.getAll())
    }

    // =====================================================================
    //  TWO-DEVICE MODE — host / join / end
    // =====================================================================

    private fun wireNearbyCallbacks() {
        nearbyService.onMessageReceived = { json ->
            // Nearby callbacks may not be on the main thread; postValue is main-safe.
            addRemoteEntry(json)
        }
        nearbyService.onConnected = {
            connectionState.postValue(ConnectionState.CONNECTED)
        }
        nearbyService.onDisconnected = {
            // Peer left / link dropped. Fall back to IDLE; local thread is untouched.
            connectionState.postValue(ConnectionState.IDLE)
        }
        nearbyService.onError = { msg ->
            connectionState.postValue(ConnectionState.ERROR)
            syncError.postValue(msg)
        }
    }

    /** HOST: generate a code, switch to two-device mode, start advertising. */
    fun startSession(): String {
        val code = generateSessionCode()
        sessionCode.value = code
        syncMode.value = SyncMode.TWO_DEVICE
        connectionState.value = ConnectionState.HOSTING
        syncError.value = null
        nearbyService.startHosting(code)
        return code
    }

    /** GUEST: switch to two-device mode, start discovering for the given code. */
    fun joinSession(code: String) {
        sessionCode.value = code
        syncMode.value = SyncMode.TWO_DEVICE
        connectionState.value = ConnectionState.JOINING
        syncError.value = null
        nearbyService.joinSession(code)
    }

    /**
     * End the two-device session and return to single-device mode.
     * RA 10173: tears down the connection AND clears the conversation so no
     * transmitted text lingers after the session ends.
     */
    fun endSession() {
        nearbyService.stop()
        syncMode.value = SyncMode.SINGLE_DEVICE
        connectionState.value = ConnectionState.IDLE
        sessionCode.value = ""
        clearConversation()
    }

    /** Wipe the conversation thread (also used by RA 10173 session-end cleanup). */
    fun clearConversation() {
        repository.clear()
        entries.postValue(emptyList())
    }

    /** Session codes look like "AK-4829" — short, readable, easy to say out loud. */
    private fun generateSessionCode(): String {
        val number = (1000..9999).random()
        return "AK-$number"
    }

    // =====================================================================
    //  MODE SWITCHING (unchanged)
    // =====================================================================

    fun switchToFSL() {
        isFSLMode.value = true
        fslService.clearBuffer()
        wordAssembly.clear()
        voskService.stopRecording()  // stop mic if user switches away mid-recording
    }

    fun switchToSpeech() {
        isFSLMode.value = false
        fslService.clearBuffer()
        wordAssembly.clear()
        voskService.loadModel()
    }

    override fun onCleared() {
        super.onCleared()
        nearbyService.stop()   // RA 10173: ensure no session survives the ViewModel
        fslService.close()
        voskService.close()
    }
}
