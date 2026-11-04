# app 구조

차량 앱(Kotlin, Jetpack Compose). MVVM + Repository 구조다.

```
Screen → ViewModel → Repository → RecordStore(차 안 저장) / Api(서버)
```

## 폴더

```
team/momentlog/app/
├─ MainActivity.kt           # 진입점. 권한 요청, ViewModel 생성
├─ Constant.kt               # 전역 상수
├─ RecorderService.kt        # 포그라운드 서비스: 신호 수집, 30초 버퍼, 기록 저장·봉인
├─ signal/                   # 차량 신호 (Vehicle SDK, 차량 속도, 시연 재생)
├─ record/                   # RecordStore(차 안 JSON 저장), SealClient(서비스용 봉인 호출)
├─ config/
│  ├─ AppNavigation.kt       # 경로(Routes)와 NavHost
│  └─ theme/                 # Colors.kt(colorXXXXXX), Theme.kt(낮/밤 토큰, QHD 크기 환산 .q/.qs)
├─ core/
│  ├─ data/api/              # Retrofit 인터페이스, ApiInfo(서버 주소·클라이언트)
│  ├─ data/model/            # 요청·응답 DTO
│  ├─ data/repository/       # ViewModel이 쓰는 유일한 데이터 진입점
│  ├─ error/                 # Failure, Either, ErrorUtil
│  └─ vehicle/               # DrivingStateMonitor(주행 중 판단)
└─ ui/
   ├─ MomentlogApp.kt        # 테마 + 화면 이동, 저장 알림·출발 안내 덮기, 주행 재개 시 대기 화면으로
   ├─ common/                # CustomButton
   ├─ models/                # 화면용 모델(RecordInfo, RecordKind, RecordFacts, Formats)
   └─ screens/
      ├─ use_case/           # ViewModel (BaseViewModel, Main, Record, Statement, Document)
      ├─ home/               # 대기
      ├─ capture/            # 저장 알림
      ├─ statement/          # 정차 후 음성 진술
      ├─ record/             # 결과 화면, 기록 목록
      ├─ document/           # 휴대폰으로 받기(QR)
      ├─ resume/             # 출발 안내
      ├─ monthly/            # 월간 점검·구독 안내
      ├─ settings/           # 설정(개발·시연용)
      └─ widget/             # 여러 화면이 같이 쓰는 부품
         ├─ car/             # CarDrawing(위에서 본 차), RoadStage(도로·차·P)
         ├─ record/          # TimelineGraph(4줄 그래프), StepTimeline(경위 단계)
         ├─ label/           # StatusChip, DriveFlag, VoiceHint, InfoNote, PlateBadge
         ├─ layout/          # AppScreen(바탕·구석 표기), SidePanel
         ├─ icon/            # AppIcon(선 아이콘)
         ├─ motion/          # EaseOut, enterFromBelow
         └─ qr/              # QrCode
```

## 규칙

- ViewModel은 상태를 StateFlow로 내보내고, 화면은 `collectAsState()`로 받는다.
- 화면은 ViewModel만 부른다. ViewModel은 Repository만 부른다(RecordStore·Api 직접 호출 금지).
- 서버 호출 결과는 `Either<Failure, T>`로 돌려준다.
- 색은 `LocalAppColors.current`(낮/밤 토큰)로 읽는다. 값은 `Colors.kt`에 두고 화면에서 직접 쓰지 않는다. 빨강(danger)은 충격 감지와 위변조에만.
- 크기는 QHD 앱 창 픽셀 값으로 `.q`(dp), `.qs`(sp)를 붙여 적는다. 실제 창 크기에 맞춰 비율로 바뀐다.
- 두 화면 이상이 쓰는 부품은 `screens/widget/` 아래 종류별 패키지에 둔다.
- 주행 중에는 대기 화면과 저장 알림만 보인다. 새 화면을 만들면 주행 재개 시 닫히게 한다(`MomentlogApp`).
- 할 일은 코드에 `TODO(앱)`으로 남긴다.

## 사용한 오픈소스

| 라이브러리 | 용도 | 라이선스 |
|---|---|---|
| [Retrofit](https://github.com/square/retrofit) | 서버 통신 | Apache 2.0 |
| [OkHttp](https://github.com/square/okhttp) | HTTP 클라이언트, 요청 로그 | Apache 2.0 |
| [ZXing core](https://github.com/zxing/zxing) | QR 그리기 | Apache 2.0 |
| [AndroidX](https://developer.android.com/jetpack/androidx) (Compose, Navigation, Lifecycle) | 화면, 화면 이동, ViewModel | Apache 2.0 |

## 빌드

### 가속도 센서 연결

실차 기록 서비스는 `TYPE_LINEAR_ACCELERATION`과 `TYPE_GRAVITY`를 약 50Hz로 구독한다.
시연 모드 전환·서비스 종료 시 모두 해제하며, 미지원/등록 실패 시 기존 차량 신호 기록은 유지한다.
센서 시각(부팅 이후 ns)은 시작 시 구한 offset으로 기록용 epoch ms로 바꾼다.
중력값이 없거나 1초 이상 차이나면 해당 선가속도 샘플을 전달하지 않는다.

장착 전제는 **화면이 객실 쪽을 향하고 센서 -Z의 수평 투영이 차량 앞쪽**인 고정 헤드유닛이다.
중력 벡터로 기울기를 보정해 앞쪽(+)·오른쪽(+)을 구하며 단위는 m/s²다.
화면 회전 설정은 센서 원좌표에 적용하지 않는다. 수평에 가까운 화면은 방향이 불명확해 입력을 보류한다.
TODO(앱): 실제 헤드유닛 센서 좌표와 장착 yaw를 실측한다. 화면이 옆을 향하거나 센서 좌표가
이 전제와 다른 차량은 `VehicleAcceleration`의 앞쪽 기준 벡터를 해당 기기에 맞춰 보정해야 한다.
중력 추정의 급가감속 응답·노면 기울기 영향과 짧은 충격의 샘플링 누락은 실차 검증이 필요하다.
1.5g 임계값과 10초 재감지 제한은 core만 담당한다.

JDK 17이 필요하다(Android Studio: Settings → Build Tools → Gradle → Gradle JDK).

```bash
./gradlew :app:assembleDebug
```


## 기기 ID와 봉인 재전송

- 앱 설치 단위 UUID를 내부 SharedPreferences에 저장하고 모든 봉인 요청의 `deviceId`로 보낸다.
  차량 번호·하드웨어 ID는 쓰지 않는다. 앱 데이터 삭제·재설치 시 새 ID가 생성된다.
- 기록 파일의 `sealed=false`가 영속 재전송 대기 상태다. 앱이 시작될 때 기존 파일을 읽고,
  새 기록 저장·네트워크 복구 시에도 전송한다. 연결 오류·408·429·5xx는 5초부터 최대 5분까지 간격을 늘려 재시도한다.
- 설치마다 32바이트 난수 토큰을 보관하고 봉인 요청에 `X-Device-Token`을 보낸다. 인증 실패(401)는 재시도하지 않는다.
- 동일한 원본 해시·기기 생성 시각·설치 ID로 재전송한다. 성공 시에만 봉인 완료를 저장한다.
  기타 4xx는 `sealRejected=true`로 자동 재시도를 중단한다. 개발자가 원인 확인 후 처리해야 한다.
- 기록 서비스를 종료해도 앱 프로세스가 살아 있는 동안 전송을 계속한다. 프로세스가 종료되면 다음 앱 실행 때 대기 상태를 복구한다.
  기존 자동 삭제 기간·보관 정책은 유지한다.
- 기록 파일 읽기·수정은 프로세스 공통 잠금으로 직렬화하고 AtomicFile로 저장한다.

## 기록 삭제와 공유 철회

- 정차 중 기록 상세의 ‘삭제’에서 확인 후 삭제한다. 기간 만료·기록 합치기에도 같은 경로를 쓴다.
- 로컬 삭제 전에 기록 ID만 철회 대기열에 남긴 뒤 `DELETE /api/v1/records/{id}/documents`를 호출한다.
  `X-Device-Id`와 봉인에 사용한 `X-Device-Token`을 보낸다.
- 연결 오류·408·429·5xx는 재시도하고, 2xx·404는 완료로 처리한다. 401·403 등은 `.rejected`로 남겨 자동 재시도를 중단한다.
  오프라인에서는 링크가 즉시 닫히지 않으며 다음 앱 실행·연결 복구 때 처리한다. 이미 받은 PDF 파일은 회수하지 않는다.
- 공유 생성과 철회를 직렬화해 삭제 도중 진행 중이던 공유 생성도 완료 뒤 철회한다.

## 진술·번호판 수정

정차 중 기록 상세의 ‘진술 고치기’로 기존 진술을 열고, 확인 화면의 ‘고치기’에서 진술과 상대 차량
번호를 직접 수정한다. ‘수정 확인’ 후 ‘이대로 저장’을 눌러 확정한다. 수정 취소는 원래 값을 유지한다.
Pleos `ON_DEVICE` 한국어 인식을 우선 사용하고 실패하면 Android `ko-KR` 인식으로 전환한다.
받아쓰기 최종 결과와 수정 확인 내용은 초안으로 저장한다. 마이크 사용이 불가능하면 ‘직접 입력’을 쓴다.
수정 중 출발하거나 화면을 벗어나면 작성 중 상태로 보관한다. 저장 실패 시 오류를 표시하고 완료 화면으로 이동하지 않는다. 진술·번호판은 원본 신호 해시와 별도로 저장한다.

Gleo는 운영사무국의 사용 가능 회신을 받았으나 승인·시연 지원 조건이 미확정이므로
2026-11-02 결정에 따라 기획만 유지하고 이번 구현·시연 범위에서 제외한다.

기록 목록의 예시 데이터 자동 삽입은 제거했다. 이전 버전의 `mock-` 예시 기록 파일은 저장소 초기화 시 삭제한다.

한국어 진술 실기기 확인: 정차 → 말하기 → “뒤에서 받았어요. 상대 차는 12가 3456이에요” 발화 →
받아쓰기 확인/수정 → 이대로 저장 → 목록에서 다시 열기 → QR 공유/PDF에 같은 진술이 표시되는지 확인한다.
마이크 거부·인식 실패·출발 중단·앱 재실행 시 초안 복원도 확인한다. 자동 빌드/단위 테스트는 실제 음성 인식 정확도를 검증하지 않는다.

## 오류 시 재시도와 나가기

- 목록 조회 실패: 오류 안내에서 ‘다시 시도’ 또는 ‘홈으로’를 고른다.
- 상세·QR·진술의 기록 조회 실패/삭제/만료: 무한 로딩 대신 오류를 표시하고 재시도·목록/이전 화면으로 돌아가기를 제공한다. 로딩 중에도 돌아갈 수 있다.
- QR 생성 실패/오프라인: 재시도·결과 화면·홈 버튼을 유지한다. 미봉인 기록은 봉인을 먼저 시도한 뒤 공유를 생성한다.
- 마이크 거부/인식 오류: 말하기 재시도·직접 입력·나중에/닫기를 제공한다. 진술 수정 화면의 뒤로가기는 수정을 취소한다.
- 진술 저장 오류: 받아 적은 내용을 유지하고 저장 재시도·닫기를 제공한다. 내용이 있는 화면에서 나가면 확인 창을 표시한다.
- 저장 완료와 화면 이탈이 겹쳐도 같은 버전의 초안이 완료 상태를 덮어쓰지 않는다.

## 서버 전송 상태 확인

- 정차 중 홈의 ‘서버 전송 확인 필요’ 또는 ‘서버 전송 대기’를 누르면 설정의 전송 상태로 이동한다.
- 봉인/공유 철회의 대기·중단 건수를 구분하고, 401(기기 인증), 403(기록 소유 기기), 그 외 요청 거부를 각각 안내한다.
  중단 원인은 기기에 남겨 앱 재실행 후에도 다시 확인할 수 있다. 토큰이나 진술 본문은 상태 안내에 포함하지 않는다.
- 네트워크 오류·408·429·5xx는 자동 재시도한다. 401·403 등은 자동 재시도하지 않으며 ‘다시 확인’을 누른 경우에만 재시도한다.
  기존 기기 ID·토큰을 그대로 사용한다. 오프라인이면 기존 거부 원인을 유지하고 연결 복구 후 확인한다.
- 공유 철회가 대기/중단 중이면 링크가 아직 열릴 수 있음을 안내한다. 완료되기 전에 닫혔다고 표시하지 않는다.
- 오류·확인 창은 기존 낮/밤 색 토큰, 카드 테두리, 글자 크기와 버튼을 사용한다. 설정의 뒤로가기는 스크롤과 무관하게 유지한다.
