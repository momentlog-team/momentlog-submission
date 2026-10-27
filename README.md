# 순간기록 (가칭)

> 사고나 위험한 순간 직전 30초의 내 차 조작(속도·브레이크·방향지시등·핸들)을 사고 경위 문장으로 바꾸고, 정차 후 진술까지 묶어 바로 제출할 수 있는 사고 정리 문서로 만들어 주는 Pleos Connect 차량 앱

현대자동차그룹 차량용 앱 개발 공모전(2026) 출품 준비 저장소입니다. **비공개 저장소**로 운영합니다.

## 현재 상태 (초안)

| 모듈 | 내용 | 상태 |
|---|---|---|
| `core/` | 30초 버퍼, 급정지 감지, 사고 기록, 경위 문장, 원본 해시 (순수 Kotlin) | 테스트 9건 통과 |
| `server/` | 원본 해시 봉인·대조 API (Spring Boot 3.3.5, Java 17) | 테스트 4건 통과 |
| `app/` | Pleos Vehicle SDK 연결, 포그라운드 서비스, 시연 재생, 기록 목록 화면 (Kotlin, Compose) | **작성만 됨, 첫 빌드 확인 필요** |

`app/`은 Android SDK가 없는 PC에서 작성했습니다. Pleos SDK 메서드 이름·시그니처는 공개 소스(`Vehicle-2.3.2-sources.jar`)로 확인했지만, 실제 빌드와 에뮬레이터 실행은 아직 하지 않았습니다.

## 실행 방법

### core·server (Android SDK 없어도 됨, JDK 17)

```bash
./gradlew :core:test          # 핵심 로직 테스트
./gradlew :server:test        # 서버 테스트
./gradlew :server:bootRun     # 서버 실행 (http://localhost:8090)
```

### app (Android SDK 필요)

1. Android Studio에서 루트 폴더를 연다. `local.properties`에 `sdk.dir`이 생기면 `:app` 모듈이 자동으로 포함된다(`settings.gradle.kts` 참고).
2. Pleos 공식 가이드대로 Pleos Connect Emulator v2.1.0(API 34) AVD를 만든다.
3. `./gradlew :app:assembleDebug` 후 설치.
4. 런타임 권한 `android.car.permission.CAR_SPEED`를 허용한다(부여 방법은 첫 실행 때 확인해 이 문서에 적는다).
5. 서버를 PC에서 띄우면 에뮬레이터 앱은 `http://10.0.2.2:8090`으로 접속한다(`app/build.gradle.kts`의 `SEAL_SERVER`).

### 시연

앱에서 **시연 재생**을 누르면 교차로 우회전 중 후방 추돌 장면(20초 후 급정지)이 재생되고, 10초 뒤 기록과 경위 문장이 목록에 나타난다. 화면에 "시연 데이터" 표시가 뜬다. 자세한 시나리오는 [`docs/demo-scenario.md`](docs/demo-scenario.md).

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0, API 34, x86_64, RAM 4GB |
| SDK | Vehicle 2.3.2, SpeechToText 3.3.1 (NaviHelper, FusedLocation은 미연결) |
| JDK | 17 |
| 빌드 | Gradle 8.14.3, AGP 8.8.2, Kotlin 2.0.21 |

## 음성 진술

정차 중 기록의 **진술 추가 → 말하기**에서 마이크 권한을 허용한다. 한국어 인식 중에는 임시 문장을 표시하고, 최종 결과 또는 **다 말했어요** 뒤 확인 화면에서 저장한다. 원본 음성 파일은 앱에 저장하지 않는다. 번호판은 인식된 텍스트가 숫자·한글 번호판 형식일 때만 추출하므로 확인이 필요하다.

음성인식은 [Pleos SpeechToText 3.3.1 공식 SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk)의 `ON_DEVICE` 모드를 먼저 시도한다. 초기화 실패·준비 시간 초과 시 [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)로 전환한다. 표준 서비스의 네트워크 사용 여부는 기기에 설치된 인식 엔진에 따라 달라진다. 음성 안내는 Android `TextToSpeech`를 통해 설치된 한국어 엔진을 사용한다. SDK는 공식 Pleos Maven 배포본을 사용하며 Pleos SDK 이용 조건을 따른다.

기기에 엔진·한국어 모델이 없거나 응답이 오지 않으면 오류를 표시한다. 화면을 벗어나거나 앱이 백그라운드로 가면 마이크를 종료하며, 주행 재개 시 받아 적은 내용은 작성 중 상태로 보관한다. 상시 음성 명령 감지는 포함하지 않는다.

차량 음성 비서의 Gleo 기록 명령은 공모전의 승인 지원 여부 확인 전까지 **비활성화**했다. 수신기는 비활성·비공개이며 스키마 초안은 APK에 포함하지 않는다. 홈의 음성 명령 안내도 숨겼다. 버튼으로 기록하기와 진술 받아쓰기는 유지한다. [연동 준비 내용](docs/gleo-integration.md)을 참고한다.

## APK

제출 규칙: 저장소 루트 `apk/` 폴더에 `팀명_앱이름_release.apk` (실행 가능한 release). 아직 없음.

## 문서

- [`docs/architecture.md`](docs/architecture.md): 구조, 데이터 흐름, 역할 분담, 다음 작업
- [`docs/api-verification.md`](docs/api-verification.md): 사용 API 교차검증 결과(권한, 반환값, 에뮬레이터 조작 가능 여부)
- [`docs/demo-scenario.md`](docs/demo-scenario.md): 1분 시연 흐름
- [`CONTRIBUTING.md`](CONTRIBUTING.md): 커밋·브랜치 규칙

## 제출 전 주의

- 블라인드 심사: 저장소, README, 영상, APK 이름, 커밋 작성자에 실명·연락처가 나오면 안 된다. 제출 전에 팀 이름 Organization으로 저장소를 옮긴다.
- 비공개 저장소로 제출하면 공지된 심사용 GitHub 계정을 Collaborator로 초대하고 마감 전에 접근을 확인한다.
- 코드와 자료는 Pleos·Android 공식 문서, 공식 샘플, 공개 SDK 소스만 참고한다. 다른 참가작의 코드·자료는 보지도 가져오지도 않는다(표절 시 수상 취소).
