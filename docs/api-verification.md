# 사용 API 검증 (2026-10-23)

## 출처 (공식 자료만)

| 출처 | 확인한 내용 |
|---|---|
| Pleos Maven 공개 소스 `ai.pleos.playground:Vehicle:2.3.2` (`-sources.jar`) | 메서드별 `@RequiresPermission`, 리스너 반환 타입 |
| Pleos 공식 문서: Vehicle 권한 표, My Project SDK 제공 상태, Virtual Console 가이드, 개발자 FAQ | 제공 상태, 에뮬레이터 조작 항목, 차량 속도 조회 방법 |
| Pleos 공식 샘플 `Pleos-Playground/pleos-sample-vehicle-app` | 서드파티에 막아 둔 API(`blockedApis`), 매니페스트 권한 선언 |
| Android 공식 문서 `VehiclePropertyIds`, `CarPropertyManager` | 차량 속도 단위·권한 |

## 순간기록이 쓰는 API

| 신호 | API | 권한 | 반환 | Virtual Console | 비고 |
|---|---|---|---|---|---|
| 브레이크 | `Brake.registerBrakePedalStatus(BrakePedalStatusListener)` | `CAR_DRIVE` | `Boolean?` 밟음 여부 | Brake Pedal 0~100% | 세기는 없음 |
| 방향지시등 | `TurnSignal.registerTurnSignalState(TurnSignalStateListener)` | `CAR_LIGHTS` | `TurnSignalValue? {OFF, RIGHT, LEFT}` | Turn Signal State | |
| 조향각 | `SteeringWheel.registerSteeringWheelAngle(SteeringWheelAngleListener)` | `CAR_DRIVE` | `Float?` 도 | Steering Angle -470~500° (음수가 왼쪽) | SDK 원값은 왼쪽이 양수. 앱이 받을 때 뒤집어 왼쪽 음수로 기록 |
| 하향등 | `Light.registerLowBeamLightState(LightStateListener)` | `CAR_LIGHTS` | `LightState? {OFF, ON, DAYTIME_RUNNING}` | Exterior Lights Mode | |
| 상향등 | `Light.registerHighBeamLightState(LightStateListener)` | `CAR_LIGHTS` | 위와 같음 | High Beam | |
| 와이퍼 | `Wiper.registerWiperState(WindowArea, WiperStateListener)` | `CAR_WINDOWS` | `WiperState? {OTHER, OFF, ON, SERVICE}` | Windshield Wiper Switch | |
| 에어백 | `Safety.registerSeatAirbagDeployed(SeatArea, SeatAirbagDeployedListener)` | `CAR_SAFETY` | `Boolean?` | **항목 없음** | 시연은 급정지 감지로 |
| 차량 속도 | Android Automotive `CarPropertyManager` `PERF_VEHICLE_SPEED` | `android.car.permission.CAR_SPEED` (런타임) | m/s | 기어 D + ACCEL/BRAKE 버튼 | Pleos FAQ가 이 방법을 안내. 에뮬레이터에서 실측 확인(아래) |

- 위 Vehicle API는 모두 조회(State) API다. Pleos 문서상 Vehicle State는 "신청 없이 사용 가능".
- 차량 제어(Vehicle Control, `CONTROL_CAR_*`)는 문서상 "준비 중". 이 앱은 쓰지 않는다.
- TTS·STT·FusedLocation은 문서상 실차 배포용 "준비 중"이지만 공모전 FAQ의 허용 SDK다. 실차용 대체(안드로이드 기본)를 둔다.
- 공식 샘플 `blockedApis`: Telltale, TirePressure, `CarInfo.getCapabilityInfo`뿐. 위 API는 막혀 있지 않다.

## 에뮬레이터 실측 (2026-10-26)

환경: Pleos Connect Emulator v2.1.0(API 34, x86_64), 에뮬레이터 37.2.12, Android Studio Rabbit 1, debug APK를 `adb install -r -g`로 설치. 로그는 `adb logcat -s Momentlog:D`.

| 신호 | Virtual Console 조작 | 앱이 받은 값 |
|---|---|---|
| 차량 속도 | 기어 D, ACCEL 6초 → BRAKE 4초 | 0 → 30.8m/s(111km/h) → 0. 약 0.1초마다 콜백 |
| 브레이크 | Brake Pedal 80% → 0% | `true` → `false` |
| 방향지시등 | Blink left → Off | `LEFT` → `OFF` |
| 조향각 | 90 → -120 | `-90.0` → `120.0` (콘솔과 부호 반대). 값이 그대로여도 약 3초마다 콜백 |
| 와이퍼 | Low → Off | `ON` → `OFF` |
| 하향등 | Low beam → Off | `ON` → `OFF` |
| 상향등 | (조작 안 함) | 시작 시 `OFF` 수신 |
| 에어백 | 콘솔 항목 없음 | 콜백 없음 |

- 권한: `-g` 설치로 `CAR_DRIVE`·`CAR_LIGHTS`·`CAR_WINDOWS`·`CAR_SAFETY`·`android.car.permission.CAR_SPEED`가 부여된다. `pleos.car.permission.CAR_SPEED`·`READ_CAR_STEERING`·`READ_WINDSHIELD_WIPERS`는 부여 목록에 없지만 신호 수신에는 문제없었다.
- 포그라운드 서비스: 기록 시작 후 `isForeground=true`로 돌면서 위 콜백을 계속 받았다.
- 급정지 감지: 111km/h에서 약 2.5초 만에 정지 → 기록 1건 저장, 경위 문장 생성 확인.
- 주행 중 화면 제한: 기어 D·주행 중에는 시스템이 앱 화면을 "주행 중에는 사용할 수 없습니다"로 가린다. 기어 P에서 다시 보인다. 시연 영상은 P에서 화면을 보여 주는 흐름으로 짠다. `cmd car_service enable-uxr false`는 user 빌드라 막혀 있다.
- Virtual Console Highway 테마를 켜 두면 차량이 D·주행 상태로 시작할 수 있다. 시작 전에 Gear Selection을 P로 둔다.

## 남은 확인 항목

- ~~조향각 부호~~: 콘솔 음수 = 왼쪽(사용자가 주행 화면으로 확인). SDK 원값은 왼쪽 양수 → `VehicleSignalClient`에서 뒤집어 저장.
- 에어백: 콘솔에 항목이 없어 확인 불가. 시연은 급정지 감지로 한다.
- 음성 입력(마이크) 사용 가능 여부
