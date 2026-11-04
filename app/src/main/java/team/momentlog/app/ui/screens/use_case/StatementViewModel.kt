package team.momentlog.app.ui.screens.use_case

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import team.momentlog.app.record.StatementWriteOrder
import team.momentlog.app.voice.StatementVoice
import team.momentlog.app.core.data.repository.RecordRepository
import team.momentlog.app.record.RecordStore

/**
 * 정차 후 음성 진술. 질문은 한 번만 한다. 진술과 번호는 센서 기록과 따로 저장한다.
 *
 * 표준 음성 서비스의 한국어 결과를 표시하고 사용자 확인 후 저장한다.
 */
class StatementViewModel(application: Application) : BaseViewModel(application) {

    enum class Step { ASK, LISTENING, CONFIRM, EDITING, LATER }

    private val recordRepository = RecordRepository(application)
    private var loadJob: Job? = null
    private var recordId: String? = null
    private val writeOrder = StatementWriteOrder()
    private var revision = 0L
    private val _recordAvailable = MutableStateFlow<Boolean?>(null)
    val recordAvailable: StateFlow<Boolean?> = _recordAvailable
    private var dirty = false
    private var pendingSpeak = false
    private var screenActive = false
    private val voice = StatementVoice(application)
    private var finishJob: Job? = null
    private var saveJob: Job? = null
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
        if (_recordAvailable.value == true && _step.value == Step.ASK && promptedId != recordId) {
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

    private val _editText = MutableStateFlow("")
    val editText: StateFlow<String> = _editText
    private val _editPlate = MutableStateFlow("")
    val editPlate: StateFlow<String> = _editPlate

    fun edit() {
        loadJob?.cancel()
        stopVoice()
        _editText.value = _transcript.value
        _editPlate.value = _plate.value.orEmpty()
        _voiceError.value = null
        _step.value = Step.EDITING
    }

    fun updateEditText(text: String) { _editText.value = text }
    fun updateEditPlate(text: String) { _editPlate.value = text }

    fun confirmEdit() {
        if (_editText.value.isBlank()) {
            _voiceError.value = "진술 내용을 입력해 주세요."
            return
        }
        applyEdit()
        _voiceError.value = null
        _step.value = Step.CONFIRM
        persistDraft()
    }

    fun cancelEdit() {
        _voiceError.value = null
        _step.value = if (_transcript.value.isBlank()) Step.ASK else Step.CONFIRM
    }

    private fun applyEdit() {
        revision++
        dirty = true
        _transcript.value = _editText.value.trim()
        _plate.value = _editPlate.value.trim().takeIf { it.isNotEmpty() }
    }

    fun start(recordId: String) {
        screenActive = true
        if (this.recordId == recordId && _step.value != Step.LATER &&
            (_recordAvailable.value == true || loadJob?.isActive == true)) return
        loadJob?.cancel()
        stopVoice()
        this.recordId = recordId
        pendingSpeak = false
        revision++
        dirty = false
        _voiceError.value = null
        _step.value = Step.ASK
        clear()
        _recordAvailable.value = null
        loadJob = viewModelScope.launch {
            try {
                val saved = recordRepository.getRecord(recordId)
                if (this@StatementViewModel.recordId != recordId) return@launch
                _recordAvailable.value = saved != null
                if (saved == null) _voiceError.value = "기록이 삭제되었거나 보관 기간이 지났어요."
                else if (_step.value == Step.ASK && saved.statement?.isNotBlank() == true) {
                    _transcript.value = saved.statement
                    _plate.value = saved.plate
                    _step.value = Step.CONFIRM
                }
                if (saved != null && pendingSpeak) { pendingSpeak = false; speak() }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _recordAvailable.value = false
                _voiceError.value = "기록을 불러오지 못했어요. 다시 시도해 주세요."
            }
        }
    }

    fun speak() {
        if (_recordAvailable.value == null) { pendingSpeak = true; return }
        if (_recordAvailable.value != true) return
        loadJob?.cancel()
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
                    persistDraft()
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
        persistDraft()
    }

    private fun stopVoice() {
        finishJob?.cancel()
        finishJob = null
        voice.cancel()
        voice.silence()
        _ready.value = false
        _level.value = 0f
    }

    fun leaveScreen(expectedId: String? = null) {
        if (expectedId != null && expectedId != recordId) return
        screenActive = false
        pendingSpeak = false
        if (_recordAvailable.value == null) loadJob?.cancel()
        stopVoice()
        if (_step.value in listOf(Step.LISTENING, Step.EDITING, Step.CONFIRM)) {
            if (_step.value == Step.EDITING) applyEdit()
            if (_partial.value.isNotBlank()) onFinal(_partial.value)
            val id = recordId
            val text = _transcript.value
            if (id != null && text.isNotBlank()) persistDraft()
            _step.value = if (_transcript.value.isBlank()) Step.ASK else Step.CONFIRM
        }
    }

    fun onPartial(text: String) {
        _partial.value = text
    }

    fun onFinal(text: String) {
        revision++
        dirty = true
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
                } else { _step.value = Step.CONFIRM; persistDraft() }
            }
        }
    }

    fun restart() = speak()

    fun save(onSaved: () -> Unit) {
        if (saveJob?.isActive == true) return
        val id = recordId ?: return
        if (_transcript.value.isBlank()) return
        val text = _transcript.value
        val plate = _plate.value
        val version = revision
        saveJob = viewModelScope.launch {
            try {
                writeStatement(id, text, plate, RecordStore.StatementState.DONE, version)
                // 이후 화면 이탈에서 완료 상태를 초안으로 덮어쓰지 않는다.
                if (recordId == id) {
                    recordId = null
                    if (screenActive) onSaved()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _voiceError.value = "진술을 저장하지 못했어요. 다시 저장해 주세요." }
        }
    }

    private suspend fun writeStatement(id: String, text: String, plate: String?, state: RecordStore.StatementState, version: Long) {
        writeOrder.write(id, version, state == RecordStore.StatementState.DONE) {
            recordRepository.saveStatement(id, text, plate, state)
        }
    }

    private fun persistDraft() {
        val id = recordId ?: return
        val text = _transcript.value
        val plate = _plate.value
        if (text.isBlank() || !dirty) return
        val version = revision
        viewModelScope.launch {
            try { writeStatement(id, text, plate, RecordStore.StatementState.WRITING, version) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { _voiceError.value = "받아 적은 내용을 저장하지 못했어요. 다시 저장해 주세요." }
        }
    }

    fun later() {
        loadJob?.cancel()
        val id = recordId ?: return
        stopVoice()
        _step.value = Step.LATER
        val text = _transcript.value
        val plate = _plate.value
        val version = revision
        viewModelScope.launch {
            try {
                writeStatement(id, text, plate, if (text.isBlank()) RecordStore.StatementState.LATER else RecordStore.StatementState.WRITING, version)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _voiceError.value = "진술 상태를 저장하지 못했어요. 다시 시도해 주세요." }
        }
    }

    /** 진술 중 출발하면 받아 적은 데까지 저장한다. */
    fun saveInterrupted() {
        val id = recordId ?: return
        if (_step.value != Step.LISTENING && _step.value != Step.EDITING) return
        if (_step.value == Step.EDITING) applyEdit()
        stopVoice()
        if (_partial.value.isNotBlank()) onFinal(_partial.value)
        val text = _transcript.value
        val plate = _plate.value
        val version = revision
        viewModelScope.launch {
            try { writeStatement(id, text, plate, RecordStore.StatementState.WRITING, version) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { _voiceError.value = "진술 초안을 저장하지 못했어요." }
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
