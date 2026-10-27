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
