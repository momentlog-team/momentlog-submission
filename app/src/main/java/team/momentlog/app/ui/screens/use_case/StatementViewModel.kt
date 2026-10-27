package team.momentlog.app.ui.screens.use_case

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import team.momentlog.app.voice.StatementVoice
import team.momentlog.app.core.data.repository.RecordRepository
import team.momentlog.app.record.RecordStore

/**
 * 정차 후 음성 진술. 질문은 한 번만 한다. 진술과 번호는 센서 기록과 따로 저장한다.
 *
 * 표준 음성 서비스의 한국어 결과를 표시하고 사용자 확인 후 저장한다.
 */
class StatementViewModel(application: Application) : BaseViewModel(application) {

    enum class Step { ASK, LISTENING, CONFIRM, LATER }

    private val recordRepository = RecordRepository(application)
    private var recordId: String? = null
    private val voice = StatementVoice(application)
    private var finishJob: Job? = null
    private var promptedId: String? = null
    private val _session = MutableStateFlow(0)
    val session: StateFlow<Int> = _session
    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    fun prompt() {
        if (_step.value == Step.ASK && promptedId != recordId) {
            promptedId = recordId
            voice.prompt()
        }
    }

    private val _step = MutableStateFlow(Step.ASK)
    val step: StateFlow<Step> = _step

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    /** 아직 확정되지 않은 뒷부분. */
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial

    private val _plate = MutableStateFlow<String?>(null)
    val plate: StateFlow<String?> = _plate

    fun start(recordId: String) {
        if (this.recordId == recordId && _step.value != Step.LATER) return
        stopVoice()
        this.recordId = recordId
        _voiceError.value = null
        _step.value = Step.ASK
        clear()
    }

    fun speak() {
        stopVoice()
        clear()
        _voiceError.value = null
        _session.value++
        _step.value = Step.LISTENING
    }

    fun beginRecognition() {
        if (_step.value != Step.LISTENING) return
        voice.start(
            onReady = { _ready.value = true },
            onPartial = { if (_step.value == Step.LISTENING) onPartial(it) },
            onFinal = {
                if (_step.value == Step.LISTENING) {
                    finishJob?.cancel()
                    onFinal(it)
                    stopVoice()
                    _step.value = Step.CONFIRM
                }
            },
            onLevel = { _level.value = it },
            onError = ::recognitionFailed,
        )
    }

    fun recognitionFailed(message: String) {
        stopVoice()
        _voiceError.value = message
        if (_partial.value.isNotBlank()) onFinal(_partial.value)
        _step.value = if (_transcript.value.isBlank()) Step.ASK else Step.CONFIRM
    }

    private fun stopVoice() {
        finishJob?.cancel()
        finishJob = null
        voice.cancel()
        voice.silence()
        _ready.value = false
        _level.value = 0f
    }

    fun leaveScreen() {
        stopVoice()
        if (_step.value == Step.LISTENING) {
            if (_partial.value.isNotBlank()) onFinal(_partial.value)
            val id = recordId
            val text = _transcript.value
            val plate = _plate.value
            if (id != null && text.isNotBlank()) viewModelScope.launch {
                recordRepository.saveStatement(id, text, plate, RecordStore.StatementState.WRITING)
            }
            _step.value = if (_transcript.value.isBlank()) Step.ASK else Step.CONFIRM
        }
    }

    fun onPartial(text: String) {
        _partial.value = text
    }

    fun onFinal(text: String) {
        _transcript.value = listOf(_transcript.value, text).filter { it.isNotBlank() }.joinToString(" ")
        _partial.value = ""
        _plate.value = PLATE.find(_transcript.value)?.value ?: _plate.value
    }

    fun finishSpeaking() {
        if (_step.value != Step.LISTENING || finishJob?.isActive == true) return
        voice.finish()
        finishJob = viewModelScope.launch {
            delay(1500)
            if (_step.value == Step.LISTENING) {
                if (_partial.value.isNotBlank()) onFinal(_partial.value)
                stopVoice()
                if (_transcript.value.isBlank()) {
                    _voiceError.value = "받아 적은 내용이 없어요. 다시 말해 주세요."
                    _step.value = Step.ASK
                } else _step.value = Step.CONFIRM
            }
        }
    }

    fun restart() = speak()

    fun save(onSaved: () -> Unit) {
        val id = recordId ?: return
        if (_transcript.value.isBlank()) return
        viewModelScope.launch {
            recordRepository.saveStatement(id, _transcript.value, _plate.value, RecordStore.StatementState.DONE)
            onSaved()
        }
    }

    fun later() {
        val id = recordId ?: return
        stopVoice()
        _step.value = Step.LATER
        viewModelScope.launch {
            recordRepository.saveStatement(id, "", null, RecordStore.StatementState.LATER)
        }
    }

    /** 진술 중 출발하면 받아 적은 데까지 저장한다. */
    fun saveInterrupted() {
        val id = recordId ?: return
        if (_step.value != Step.LISTENING) return
        stopVoice()
        if (_partial.value.isNotBlank()) onFinal(_partial.value)
        viewModelScope.launch {
            recordRepository.saveStatement(id, _transcript.value, _plate.value, RecordStore.StatementState.WRITING)
        }
        recordId = null
        _step.value = Step.ASK
    }

    override fun onCleared() {
        stopVoice()
        voice.close()
        super.onCleared()
    }

    private fun clear() {
        _transcript.value = ""
        _partial.value = ""
        _plate.value = null
    }

    private companion object {
        /** 국내 번호판: "12가 3456", "123나4567". */
        val PLATE = Regex("""\d{2,3}\s?[가-힣]\s?\d{4}""")
    }
}
