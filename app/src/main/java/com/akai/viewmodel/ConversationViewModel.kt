package com.akai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.akai.data.ConversationEntry
import com.akai.data.ConversationRepository
import com.akai.data.SenderType
import com.akai.service.FSLRecognitionService
import com.akai.service.VoskSTTService
import com.akai.service.WordAssemblyLayer

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

    init {
        fslService.loadModel()
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
