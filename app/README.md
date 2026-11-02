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

JDK 17이 필요하다(Android Studio: Settings → Build Tools → Gradle → Gradle JDK).

```bash
./gradlew :app:assembleDebug
```


## 기기 ID와 봉인 재전송

- 앱 설치 단위 UUID를 내부 SharedPreferences에 저장하고 모든 봉인 요청의 `deviceId`로 보낸다.
  차량 번호·하드웨어 ID는 쓰지 않는다. 앱 데이터 삭제·재설치 시 새 ID가 생성된다.
- 기록 파일의 `sealed=false`가 영속 재전송 대기 상태다. 기록 서비스가 시작될 때 기존 파일을 읽고,
  새 기록 저장·네트워크 복구 시에도 전송한다. 연결 오류·408·429·5xx는 5초부터 최대 5분까지 간격을 늘려 재시도한다.
- 동일한 원본 해시·기기 생성 시각·설치 ID로 재전송한다. 성공 시에만 봉인 완료를 저장한다.
  기타 4xx는 `sealRejected=true`로 자동 재시도를 중단한다. 개발자가 원인 확인 후 처리해야 한다.
- 기록 서비스를 사용자가 종료하면 전송도 멈추지만, 대기 상태는 파일에 남아 다음 서비스 시작 때 복구된다.
  기존 자동 삭제 기간·보관 정책은 유지하며 mock 예제 파일은 재전송하지 않는다.
- 기록 파일 읽기·수정은 프로세스 공통 잠금으로 직렬화하고 AtomicFile로 저장한다.

## 진술·번호판 수정

정차 중 기록 상세의 ‘진술 고치기’로 기존 진술을 열고, 확인 화면의 ‘고치기’에서 진술과 상대 차량
번호를 직접 수정한다. ‘수정 확인’ 후 ‘이대로 저장’을 눌러 확정한다. 수정 취소는 원래 값을 유지한다.
수정 중 출발하거나 화면을 벗어나면 작성 중 상태로 보관한다. 진술·번호판은 원본 신호 해시와 별도로 저장한다.

Gleo는 운영사무국의 사용 가능 회신을 받았으나 승인·시연 지원 조건이 미확정이므로
2026-11-02 결정에 따라 기획만 유지하고 이번 구현·시연 범위에서 제외한다.
