package team.momentlog.app.ui.screens.use_case

import kotlinx.coroutines.Job
import java.net.URI
import team.momentlog.app.core.data.model.record.DocumentResponse
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

/** 공유 생성 결과가 도착한 뒤에만 토큰 주소를 QR로 표시한다. */
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

    private val _share = MutableStateFlow<DocumentResponse?>(null)
    val share: StateFlow<DocumentResponse?> = _share
    private var shareJob: Job? = null

    fun documentUrl(recordId: String): String? = _share.value
        ?.takeIf { it.recordId == recordId }
        ?.let { URI(BuildConfig.DOCUMENT_BASE_URL).resolve(it.path).toString() }

    fun createDocument(record: RecordInfo) {
        shareJob?.cancel()
        _openedAtMillis.value = null
        _failure.value = null
        _verify.value = null
        _share.value = null
        shareJob = viewModelScope.launch {
            withLoading { recordRepository.createDocument(record.id) }.fold(
                left = { _failure.value = it },
                right = { _share.value = it },
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
