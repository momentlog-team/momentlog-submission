package team.momentlog.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener

/** Pleos 차량 내 인식을 우선 사용하고 표준 인식 서비스로 대체한다. 음성 파일은 저장하지 않는다. */
class StatementVoice(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    private var pleos: SpeechToText? = null
    private var pleosListener: ResultListener? = null
    private var timeout: Runnable? = null
    private var tts: TextToSpeech? = null
    private var promptPending = false
    private var ttsReady = false

    fun prompt() {
        promptPending = true
        if (ttsReady) readPrompt()
        else if (tts == null) tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady && promptPending) readPrompt()
        }
    }

    private fun readPrompt() {
        promptPending = false
        val engine = tts ?: return
        if (engine.setLanguage(Locale.KOREA) >= TextToSpeech.LANG_AVAILABLE) {
            engine.speak("괜찮으세요? 말하기 버튼을 누르고 상황을 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH, null, "statement")
        }
    }

    fun silence() { promptPending = false; tts?.stop() }

    fun start(onReady: () -> Unit, onPartial: (String) -> Unit, onFinal: (String) -> Unit,
              onLevel: (Float) -> Unit, onError: (String) -> Unit) {
        cancel()
        silence()
        val token = generation
        fun androidFallback() {
            if (token != generation) return
            generation++
            releasePleos()
            clearTimeout()
            startAndroid(onReady, onPartial, onFinal, onLevel, onError)
        }
        try {
            var started = false
            val speech = SpeechToText(context, Mode.ON_DEVICE)
            pleos = speech
            val listener = object : ResultListener {
                override fun onReady() = Unit
                override fun onStartedRecognition() { handler.post { if (token == generation) { started = true; clearTimeout(); onReady() } } }
                override fun onUpdated(stt: String, completed: Boolean) { handler.post {
                    if (token != generation) return@post
                    if (completed) {
                        cancel()
                        if (stt.isBlank()) onError("말씀을 듣지 못했어요. 다시 말해 주세요.") else onFinal(stt)
                    } else { started = true; clearTimeout(); onReady(); onPartial(stt) }
                } }
                override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                override fun onUpdatedRms(rms: Double) { handler.post {
                    if (token == generation) {
                        started = true; clearTimeout(); onReady()
                        onLevel((rms / 3000.0).toFloat().coerceIn(0f, 1f))
                    }
                } }
                override fun onEndedRecognition() { handler.post {
                    if (token == generation) {
                        clearTimeout()
                        timeout = Runnable {
                            if (token == generation) {
                                cancel()
                                onError("음성인식이 종료됐어요. 받아 적은 내용을 확인하거나 다시 말해 주세요.")
                            }
                        }.also { handler.postDelayed(it, 1500) }
                    }
                } }
                override fun onError(errorType: ErrorType) { handler.post {
                    if (token != generation) return@post
                    if (!started) androidFallback() else {
                        cancel()
                        onError("음성인식이 중단됐어요. 받아 적은 내용을 확인하거나 다시 말해 주세요.")
                    }
                } }
            }
            pleosListener = listener
            speech.initialize(object : OnInitializeListener {
                override fun onSuccess() { handler.post {
                    if (token != generation || pleos !== speech) return@post
                    try {
                        speech.addListener(listener)
                        speech.setLocale(Locale.KOREA)
                        speech.request()
                    } catch (_: Exception) { androidFallback() }
                } }
                override fun onFailure(errorType: ErrorType) { handler.post { androidFallback() } }
            })
            // 첫 한국어 모델 로딩은 에뮬레이터에서 10초 이상 걸릴 수 있다.
            timeout = Runnable { androidFallback() }.also { handler.postDelayed(it, 30_000) }
        } catch (_: Exception) { androidFallback() }
    }

    private fun startAndroid(onReady: () -> Unit, onPartial: (String) -> Unit, onFinal: (String) -> Unit,
                             onLevel: (Float) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("이 기기에서 음성인식을 사용할 수 없어요. 나중에 다시 시도해 주세요.")
            return
        }
        val token = generation
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (token == generation) { clearTimeout(); onReady() } }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) { if (token == generation) onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    if (token == generation) onPartial(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onResults(results: Bundle?) {
                    if (token != generation) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    cancel()
                    if (text.isBlank()) onError("말씀을 듣지 못했어요. 다시 말해 주세요.") else onFinal(text)
                }
                override fun onError(error: Int) {
                    if (token != generation) return
                    cancel()
                    onError(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한을 허용해야 받아쓸 수 있어요."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말씀을 듣지 못했어요. 다시 말해 주세요."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성인식 연결이 원활하지 않아요. 연결을 확인하고 다시 시도해 주세요."
                        SpeechRecognizer.ERROR_AUDIO -> "마이크를 사용할 수 없어요. 다른 음성 기능을 종료하고 다시 시도해 주세요."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "이 기기에서 한국어 음성인식을 사용할 수 없어요."
                        else -> "음성인식을 시작하지 못했어요. 잠시 후 다시 시도해 주세요."
                    })
                }
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
            timeout = Runnable {
                if (token == generation) {
                    cancel()
                    onError("음성인식 서비스가 응답하지 않아요. 기기의 음성 지원과 마이크 설정을 확인해 주세요.")
                }
            }.also { handler.postDelayed(it, 8000) }
        } catch (_: Exception) {
            cancel()
            onError("음성인식 서비스를 사용할 수 없어요. 권한과 기기 지원을 확인해 주세요.")
        }
    }

    fun finish() {
        runCatching { pleos?.stop() }
        runCatching { recognizer?.stopListening() }
    }
    private fun clearTimeout() { timeout?.let(handler::removeCallbacks); timeout = null }
    private fun releasePleos() {
        val speech = pleos
        pleos = null
        val listener = pleosListener
        pleosListener = null
        if (speech != null) {
            runCatching { if (listener != null) speech.removeListener(listener) }
            runCatching { speech.stop() }
            // SDK의 stop()은 비동기 Binder 호출이므로 연결 해제를 다음 작업으로 넘긴다.
            handler.postDelayed({ runCatching { speech.release() } }, 500)
        }
    }
    fun cancel() {
        generation++
        clearTimeout()
        releasePleos()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
    fun close() { cancel(); silence(); tts?.shutdown(); tts = null; ttsReady = false }
}
