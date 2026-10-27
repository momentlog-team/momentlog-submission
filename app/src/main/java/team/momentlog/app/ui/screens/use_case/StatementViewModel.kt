package team.momentlog.app.ui.screens.use_case

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import team.momentlog.app.core.data.repository.RecordRepository
import team.momentlog.app.record.RecordStore

/**
 * 정차 후 음성 진술. 질문은 한 번만 한다. 진술과 번호는 센서 기록과 따로 저장한다.
 *
 * TODO(앱): TextToSpeech로 "괜찮으세요? 상황을 말씀해 주시면 기록해 둘게요"를 읽고,
 *  SpeechRecognizer 부분 결과를 [onPartial], 최종 결과를 [onFinal]로 넣기(voice 패키지).
 *  번호판은 최종 문장에서 "12가 3456" 형식을 찾아 [plate]에 넣는다.
 */
class StatementViewModel(application: Application) : BaseViewModel(application) {

    enum class Step { ASK, LISTENING, CONFIRM, LATER }

    private val recordRepository = RecordRepository(application)
    private var recordId: String? = null

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
        if (this.recordId == recordId) return
        this.recordId = recordId
        _step.value = Step.ASK
        clear()
    }

    fun speak() {
        clear()
        _step.value = Step.LISTENING
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
        if (_partial.value.isNotBlank()) onFinal(_partial.value)
        _step.value = Step.CONFIRM
    }

    fun restart() = speak()

    fun save(onSaved: () -> Unit) {
        val id = recordId ?: return
        viewModelScope.launch {
            recordRepository.saveStatement(id, _transcript.value, _plate.value, RecordStore.StatementState.DONE)
            onSaved()
        }
    }

    fun later() {
        val id = recordId ?: return
        _step.value = Step.LATER
        viewModelScope.launch {
            recordRepository.saveStatement(id, "", null, RecordStore.StatementState.LATER)
        }
    }

    /** 진술 중 출발하면 받아 적은 데까지 저장한다. */
    fun saveInterrupted() {
        val id = recordId ?: return
        if (_step.value != Step.LISTENING) return
        if (_partial.value.isNotBlank()) onFinal(_partial.value)
        viewModelScope.launch {
            recordRepository.saveStatement(id, _transcript.value, _plate.value, RecordStore.StatementState.WRITING)
        }
        recordId = null
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
