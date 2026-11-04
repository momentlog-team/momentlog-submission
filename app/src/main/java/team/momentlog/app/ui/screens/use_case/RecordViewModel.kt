package team.momentlog.app.ui.screens.use_case

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import team.momentlog.app.core.error.Failure
import team.momentlog.app.core.data.repository.RecordRepository
import team.momentlog.app.ui.models.RecordInfo
import team.momentlog.core.IncidentNarrator

/** 기록 목록과 결과 화면. 새 기록이 저장되면 목록을 다시 읽는다. */
class RecordViewModel(application: Application) : BaseViewModel(application) {

    private val recordRepository = RecordRepository(application)
    private val narrator = IncidentNarrator()

    private val _records = MutableStateFlow<List<RecordInfo>>(emptyList())
    val records: StateFlow<List<RecordInfo>> = _records

    private val _selected = MutableStateFlow<RecordInfo?>(null)
    val selected: StateFlow<RecordInfo?> = _selected
    private val _focusedId = MutableStateFlow<String?>(null)
    val focusedId: StateFlow<String?> = _focusedId
    private var detailJob: Job? = null
    private var requestedId: String? = null
    private val _loadedId = MutableStateFlow<String?>(null)
    val loadedId: StateFlow<String?> = _loadedId
    private val _recordFailure = MutableStateFlow<String?>(null)
    val recordFailure: StateFlow<String?> = _recordFailure
    var listIndex: Int = 0
        private set
    var listOffset: Int = 0
        private set

    fun focusRecord(id: String) { _focusedId.value = id }
    fun resetListPosition() {
        _focusedId.value = null
        listIndex = 0
        listOffset = 0
    }

    fun rememberListPosition(index: Int, offset: Int) {
        listIndex = index
        listOffset = offset
    }

    init {
        viewModelScope.launch {
            recordRepository.changes.collect {
                loadRecords()
                requestedId?.let(::loadRecord)
            }
        }
    }

    fun loadRecords() {
        viewModelScope.launch {
            try {
                _failure.value = null
                _records.value = withLoading { recordRepository.getRecords().map { RecordInfo.from(it, narrator) } }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _failure.value = Failure(message = "기록 목록을 불러오지 못했어요. 다시 시도해 주세요.") }
        }
    }

    fun loadRecord(recordId: String) {
        requestedId = recordId
        focusRecord(recordId)
        detailJob?.cancel()
        _loadedId.value = null
        _recordFailure.value = null
        detailJob = viewModelScope.launch {
            try {
                _selected.value = recordRepository.getRecord(recordId)?.let { RecordInfo.from(it, narrator) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _selected.value = null
                _recordFailure.value = "기록을 불러오지 못했어요. 다시 시도해 주세요."
            }
            _loadedId.value = recordId
        }
    }

    /** 7일 자동 삭제에서 뺀다. */
    fun retain(recordId: String) {
        viewModelScope.launch {
            try {
                recordRepository.retain(recordId)
                loadRecords()
                if (_selected.value?.id == recordId) loadRecord(recordId)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _failure.value = Failure(message = "보관 상태를 저장하지 못했어요. 다시 시도해 주세요.") }
        }
    }

    fun delete(recordId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                recordRepository.delete(recordId)
                if (requestedId == recordId) { requestedId = null; detailJob?.cancel() }
                if (_selected.value?.id == recordId) _selected.value = null
                loadRecords()
                onDeleted()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _failure.value = team.momentlog.app.core.error.Failure(message = "기록을 삭제하지 못했어요. 다시 시도해 주세요.")
            }
        }
    }
}
