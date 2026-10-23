package team.momentlog.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import team.momentlog.app.record.RecordStore
import team.momentlog.core.IncidentNarrator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 초안 화면: 기록 시작/중지, 시연 재생, 저장된 기록과 경위 문장 목록.
 * TODO(앱): 주행 중 간편 화면(CarUxRestrictions), 기록 상세(그래프), 문서 내보내기·QR.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(
            arrayOf("android.car.permission.CAR_SPEED", Manifest.permission.POST_NOTIFICATIONS),
            0,
        )
        val store = RecordStore(this)
        setContent { MaterialTheme { Screen(store) } }
    }

    @Composable
    private fun Screen(store: RecordStore) {
        val status by RecorderService.status.collectAsState()
        val lastSaved by RecorderService.lastSavedId.collectAsState()
        val records = remember(lastSaved) { store.list() }
        val narrator = remember { IncidentNarrator() }
        val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA) }

        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("순간기록", style = MaterialTheme.typography.headlineMedium)
            Text(
                when (status) {
                    RecorderService.Status.RECORDING -> "기록 중: 최근 30초를 들고 있어요"
                    RecorderService.Status.REPLAYING -> "시연 데이터 재생 중 (실제 차량 신호 아님)"
                    RecorderService.Status.STOPPED -> "꺼져 있음"
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { RecorderService.start(this@MainActivity, replay = false) }) { Text("기록 시작") }
                OutlinedButton(onClick = { RecorderService.start(this@MainActivity, replay = true) }) { Text("시연 재생") }
                OutlinedButton(onClick = { RecorderService.stop(this@MainActivity) }) { Text("중지") }
            }
            Text("저장된 기록 ${records.size}건", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records, key = { it.record.id }) { saved ->
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${timeFormat.format(Date(saved.record.triggerAtMillis))} · ${saved.record.trigger.label}" +
                                    if (saved.sealed) " · 원본 봉인됨" else " · 봉인 대기",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            narrator.narrate(saved.record).forEach { Text(it) }
                        }
                    }
                }
            }
        }
    }
}
