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
│  └─ theme/                 # Colors.kt(colorXXXXXX), Theme.kt
├─ core/
│  ├─ data/api/              # Retrofit 인터페이스, ApiInfo(서버 주소·클라이언트)
│  ├─ data/model/            # 요청·응답 DTO
│  ├─ data/repository/       # ViewModel이 쓰는 유일한 데이터 진입점
│  ├─ error/                 # Failure, Either, ErrorUtil
│  └─ vehicle/               # DrivingStateMonitor(주행 중 판단)
└─ ui/
   ├─ MomentlogApp.kt        # 공통 틀 + 화면 이동, 주행 시작 시 홈으로
   ├─ common/                # AppScaffold(상단 바·왼쪽 레일), CustomButton
   ├─ models/                # 화면용 모델(RecordInfo)
   └─ screens/
      ├─ use_case/           # ViewModel (BaseViewModel, Main, Record, Statement, Document)
      ├─ home/               # 1 홈
      ├─ capture/            # 2 순간 저장 오버레이
      ├─ statement/          # 3 정차 후 음성 진술
      ├─ record/             # 4 기록 상세, 6 기록 목록
      ├─ document/           # 5 문서 받기
      ├─ settings/           # 설정
      └─ widget/             # 화면 안 부품 (record/TimelineGraph)
```

## 규칙

- ViewModel은 상태를 StateFlow로 내보내고, 화면은 `collectAsState()`로 받는다.
- 화면은 ViewModel만 부른다. ViewModel은 Repository만 부른다(RecordStore·Api 직접 호출 금지).
- 서버 호출 결과는 `Either<Failure, T>`로 돌려준다.
- 색은 `Colors.kt`의 상수만 쓴다. 빨강 = 기록·충돌, 노랑 = 방향지시등·주행 중, 초록 = 정상·봉인, 파랑 = 주요 버튼.
- 주행 중에는 홈만 보인다. 새 화면을 만들면 주행 중에 열리지 않게 한다.
- 할 일은 코드에 `TODO(앱)`으로 남긴다.

## 사용한 오픈소스

| 라이브러리 | 용도 | 라이선스 |
|---|---|---|
| [Retrofit](https://github.com/square/retrofit) | 서버 통신 | Apache 2.0 |
| [OkHttp](https://github.com/square/okhttp) | HTTP 클라이언트, 요청 로그 | Apache 2.0 |
| [AndroidX](https://developer.android.com/jetpack/androidx) (Compose, Navigation, Lifecycle) | 화면, 화면 이동, ViewModel | Apache 2.0 |

## 빌드

JDK 17이 필요하다(Android Studio: Settings → Build Tools → Gradle → Gradle JDK).

```bash
./gradlew :app:assembleDebug
```
