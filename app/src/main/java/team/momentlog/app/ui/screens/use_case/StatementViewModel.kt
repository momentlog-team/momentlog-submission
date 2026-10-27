package team.momentlog.app.ui.screens.use_case

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 정차 후 음성 진술(3). 질문 3단계를 차례로 진행한다.
 * TODO(앱): TextToSpeech로 질문을 읽고 SpeechToText로 받아 적기(voice 패키지), 진술 저장·봉인
 */
class StatementViewModel(application: Application) : BaseViewModel(application) {

    enum class Step(val question: String) {
        CHECK_SAFE("괜찮으세요?"),
        SITUATION("무슨 일이 있었는지 말씀해 주세요"),
        COUNTERPART("상대 차량 번호를 말씀해 주세요"),
    }

    private val _step = MutableStateFlow(Step.CHECK_SAFE)
    val step: StateFlow<Step> = _step

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val answers = mutableMapOf<Step, String>()

    fun start(recordId: String) {
        answers.clear()
        _step.value = Step.CHECK_SAFE
        _transcript.value = ""
    }

    /** 다음 질문으로. 마지막 질문이면 true를 돌려준다. */
    fun next(): Boolean {
        answers[_step.value] = _transcript.value
        val following = Step.entries.getOrNull(_step.value.ordinal + 1) ?: return true
        _step.value = following
        _transcript.value = ""
        return false
    }

    fun retry() {
        _transcript.value = ""
    }
}
