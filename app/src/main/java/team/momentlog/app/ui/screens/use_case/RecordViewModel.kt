package team.momentlog.app.ui.screens.use_case

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import team.momentlog.app.RecorderService
import team.momentlog.app.core.data.repository.RecordRepository
import team.momentlog.app.ui.models.RecordInfo
import team.momentlog.core.IncidentNarrator

/** 기록 목록(6)과 기록 상세(4). 새 기록이 저장되면 목록을 다시 읽는다. */
class RecordViewModel(application: Application) : BaseViewModel(application) {

    private val recordRepository = RecordRepository(application)
    private val narrator = IncidentNarrator()

    private val _records = MutableStateFlow<List<RecordInfo>>(emptyList())
    val records: StateFlow<List<RecordInfo>> = _records

    private val _selected = MutableStateFlow<RecordInfo?>(null)
    val selected: StateFlow<RecordInfo?> = _selected

    init {
        viewModelScope.launch {
            RecorderService.lastSavedId.collect { loadRecords() }
        }
    }

    fun loadRecords() {
        viewModelScope.launch {
            _records.value = withLoading {
                recordRepository.getRecords().map { RecordInfo.from(it, narrator) }
            }
        }
    }

    fun loadRecord(recordId: String) {
        viewModelScope.launch {
            _selected.value = withLoading {
                recordRepository.getRecord(recordId)?.let { RecordInfo.from(it, narrator) }
            }
        }
    }

    // TODO(앱): 보관(retain), 삭제
}
