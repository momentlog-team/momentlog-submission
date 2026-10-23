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
| 조향각 | `SteeringWheel.registerSteeringWheelAngle(SteeringWheelAngleListener)` | `CAR_DRIVE` | `Float?` 도 | Steering Angle ±540° | 부호(왼쪽 음수 여부)·콜백 주기 직접 확인 필요 |
| 하향등 | `Light.registerLowBeamLightState(LightStateListener)` | `CAR_LIGHTS` | `LightState? {OFF, ON, DAYTIME_RUNNING}` | Exterior Lights Mode | |
| 상향등 | `Light.registerHighBeamLightState(LightStateListener)` | `CAR_LIGHTS` | 위와 같음 | High Beam | |
| 와이퍼 | `Wiper.registerWiperState(WindowArea, WiperStateListener)` | `CAR_WINDOWS` | `WiperState? {OTHER, OFF, ON, SERVICE}` | Windshield Wiper Switch | |
| 에어백 | `Safety.registerSeatAirbagDeployed(SeatArea, SeatAirbagDeployedListener)` | `CAR_SAFETY` | `Boolean?` | **항목 없음** | 시연은 급정지 감지로 |
| 차량 속도 | Android Automotive `CarPropertyManager` `PERF_VEHICLE_SPEED` | `android.car.permission.CAR_SPEED` (런타임) | m/s | **항목 없음** | Pleos FAQ가 이 방법을 안내. 에뮬레이터에서 값을 바꿀 방법 직접 확인 필요, 안 되면 앱 내 재생 |

- 위 Vehicle API는 모두 조회(State) API다. Pleos 문서상 Vehicle State는 "신청 없이 사용 가능".
- 차량 제어(Vehicle Control, `CONTROL_CAR_*`)는 문서상 "준비 중". 이 앱은 쓰지 않는다.
- TTS·STT·FusedLocation은 문서상 실차 배포용 "준비 중"이지만 공모전 FAQ의 허용 SDK다. 실차용 대체(안드로이드 기본)를 둔다.
- 공식 샘플 `blockedApis`: Telltale, TirePressure, `CarInfo.getCapabilityInfo`뿐. 위 API는 막혀 있지 않다.

## 첫 에뮬레이터 실행 때 직접 확인할 것

- 런타임 권한(`CAR_SPEED`) 부여 방법과 앱 실행 사용자
- 에뮬레이터가 안정적으로 도는 실행 옵션(GPU 설정)
- 각 리스너 콜백이 오는 주기와 Virtual Console 조작 반영 여부
- 조향각 부호(왼쪽이 음수인지)
- 포그라운드 서비스 상태에서 화면이 가려져도 콜백이 계속 오는지
- 음성 입력(마이크) 사용 가능 여부
