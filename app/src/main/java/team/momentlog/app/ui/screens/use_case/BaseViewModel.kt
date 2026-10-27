package team.momentlog.app.ui.screens.use_case

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import team.momentlog.app.core.error.Failure

/** 모든 ViewModel이 공유하는 로딩·오류 상태. */
abstract class BaseViewModel(application: Application) : AndroidViewModel(application) {

    protected val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    protected val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure

    fun clearFailure() {
        _failure.value = null
    }

    protected suspend fun <T> withLoading(block: suspend () -> T): T {
        _isLoading.value = true
        try {
            return block()
        } finally {
            _isLoading.value = false
        }
    }
}
