package com.akai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.akai.data.ConversationEntry
import com.akai.data.ConversationRepository
import com.akai.data.SenderType
import com.akai.service.FSLRecognitionService
import com.akai.service.VoskSTTService
import com.akai.service.WordAssemblyLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    // Services
    val fslService = FSLRecognitionService(application)
    val voskService = VoskSTTService(application)
    val wordAssembly = WordAssemblyLayer()

    // Repository
    private val repository = ConversationRepository()

    // Observable state
    val entries = MutableLiveData<List<ConversationEntry>>(emptyList())
    val isFSLMode = MutableLiveData<Boolean>(true)
    val isRecording = MutableLiveData<Boolean>(false)
    val modelsReady = MutableLiveData<Boolean>(false)

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
    }

    fun addDeafMessage(text: String) {
        val entry = ConversationEntry(text = text, sender = SenderType.DEAF)
        repository.addEntry(entry)
        entries.postValue(repository.getAll())
    }

    fun addHearingMessage(text: String) {
        val entry = ConversationEntry(text = text, sender = SenderType.HEARING)
        repository.addEntry(entry)
        entries.postValue(repository.getAll())
    }

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
        fslService.close()
        voskService.close()
    }
}
