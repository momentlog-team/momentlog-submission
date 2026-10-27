package team.momentlog.app.ui.screens.use_case

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import team.momentlog.app.BuildConfig
import team.momentlog.app.core.data.model.record.VerifyResponse
import team.momentlog.app.core.data.repository.RecordRepository
import team.momentlog.app.ui.models.RecordInfo

/**
 * 휴대폰으로 문서 받기. 원본 봉인 상태를 확인하고 문서 주소를 QR로 넘긴다.
 *
 * TODO(앱): QR 내용을 "기록 ID + 서명" 짧은 URL로 바꾸기(형식은 백엔드 A·B와 맞춤).
 *  휴대폰에서 링크를 열면 서버 알림 또는 주기 확인으로 [markOpened]를 부르기.
 *  통신이 없을 때 원본 지문을 들고 있다가 연결되면 봉인 재전송하기.
 */
class DocumentViewModel(application: Application) : BaseViewModel(application) {

    private val recordRepository = RecordRepository(application)
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)

    private val _verify = MutableStateFlow<VerifyResponse?>(null)
    val verify: StateFlow<VerifyResponse?> = _verify

    private val _online = MutableStateFlow(connectivity?.activeNetwork != null)
    val online: StateFlow<Boolean> = _online

    private val _openedAtMillis = MutableStateFlow<Long?>(null)
    val openedAtMillis: StateFlow<Long?> = _openedAtMillis

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = true
        }

        override fun onLost(network: Network) {
            _online.value = connectivity?.activeNetwork != null
        }
    }

    init {
        connectivity?.registerDefaultNetworkCallback(networkCallback)
    }

    fun documentUrl(record: RecordInfo): String = BuildConfig.DOCUMENT_BASE_URL + record.id

    fun checkSeal(record: RecordInfo) {
        _openedAtMillis.value = null
        viewModelScope.launch {
            withLoading { recordRepository.verify(record.id, record.sha256) }.fold(
                left = { _failure.value = it },
                right = { _verify.value = it },
            )
        }
    }

    fun markOpened(atMillis: Long) {
        _openedAtMillis.value = atMillis
    }

    override fun onCleared() {
        connectivity?.unregisterNetworkCallback(networkCallback)
        super.onCleared()
    }
}
