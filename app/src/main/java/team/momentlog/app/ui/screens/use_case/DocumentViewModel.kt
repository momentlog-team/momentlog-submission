package team.momentlog.app.ui.screens.use_case

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import team.momentlog.app.core.data.model.record.VerifyResponse
import team.momentlog.app.core.data.repository.RecordRepository
import team.momentlog.app.ui.models.RecordInfo

/**
 * 문서 받기(5). 원본 봉인 상태를 확인하고, 문서 주소를 QR로 넘긴다.
 * TODO(앱): 백엔드B의 사고 정리 문서 API가 나오면 문서 주소를 받아 QR로 그리기(zxing-core)
 */
class DocumentViewModel(application: Application) : BaseViewModel(application) {

    private val recordRepository = RecordRepository(application)

    private val _verify = MutableStateFlow<VerifyResponse?>(null)
    val verify: StateFlow<VerifyResponse?> = _verify

    fun checkSeal(record: RecordInfo) {
        viewModelScope.launch {
            withLoading { recordRepository.verify(record.id, record.sha256) }.fold(
                left = { _failure.value = it },
                right = { _verify.value = it },
            )
        }
    }
}
