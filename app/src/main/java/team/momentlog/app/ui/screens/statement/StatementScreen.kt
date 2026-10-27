package team.momentlog.app.ui.screens.statement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.config.theme.color9E9E9E
import team.momentlog.app.config.theme.colorE5E5E5
import team.momentlog.app.config.theme.colorEAF1FF
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.use_case.StatementViewModel

/** 3 정차 후 음성 진술. 왼쪽은 질문 3단계, 오른쪽은 지금 질문과 받아 적은 내용. */
@Composable
fun StatementScreen(
    recordId: String,
    statementViewModel: StatementViewModel,
    onLater: () -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(recordId) { statementViewModel.start(recordId) }
    val step by statementViewModel.step.collectAsState()
    val transcript by statementViewModel.transcript.collectAsState()

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatementViewModel.Step.entries.forEach { item ->
                val current = item == step
                val done = item.ordinal < step.ordinal
                Text(
                    "${item.ordinal + 1}  ${item.question}",
                    fontSize = 16.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (current || done) color111111 else color9E9E9E,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (current) colorEAF1FF else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("기록마다 한 번만 물어봐요", fontSize = 13.sp, color = color6B6B6B)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("“${step.question}”", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = color111111)
            Text("듣고 있어요", fontSize = 15.sp, color = color6B6B6B)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .border(BorderStroke(1.dp, colorE5E5E5), RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("받아 적은 내용", fontSize = 13.sp, color = color6B6B6B)
                Text(transcript.ifEmpty { "…" }, fontSize = 18.sp, color = color111111)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomButton("나중에", onClick = onLater)
                CustomButton("다시 말하기", onClick = statementViewModel::retry)
                Spacer(Modifier.weight(1f))
                CustomButton("다음", onClick = { if (statementViewModel.next()) onDone() }, primary = true)
            }
        }
    }
}
