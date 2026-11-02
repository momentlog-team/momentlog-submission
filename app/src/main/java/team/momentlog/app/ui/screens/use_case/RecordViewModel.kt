package team.momentlog.app.ui.screens.use_case

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import team.momentlog.app.core.data.repository.RecordRepository
import team.momentlog.app.ui.models.RecordInfo
import team.momentlog.core.IncidentNarrator
import team.momentlog.app.BuildConfig

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
                _selected.value?.id?.let(::loadRecord)
            }
        }
    }

    fun loadRecords() {
        viewModelScope.launch {
            _records.value = withLoading {
                if (BuildConfig.DEBUG) recordRepository.ensureMockRecords()
                recordRepository.getRecords().map { RecordInfo.from(it, narrator) }
            }
        }
    }

    fun loadRecord(recordId: String) {
        focusRecord(recordId)
        viewModelScope.launch {
            _selected.value = withLoading {
                recordRepository.getRecord(recordId)?.let { RecordInfo.from(it, narrator) }
            }
        }
    }

    /** 7일 자동 삭제에서 뺀다. */
    fun retain(recordId: String) {
        viewModelScope.launch {
            recordRepository.retain(recordId)
            loadRecords()
            if (_selected.value?.id == recordId) loadRecord(recordId)
        }
    }

    // TODO(앱): 삭제
}
