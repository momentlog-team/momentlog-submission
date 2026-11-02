# Gleo 음성 기록 연동

## 현재 상태: 기획만 유지, 구현·시연 범위에서 제외

운영사무국 회신에서 Gleo Interface를 공모전 앱에서 사용할 수 있다는 점은 확인됐다.
다만 신청·승인 절차, App Schema 등록·처리 기간, 에뮬레이터·PT 지원 범위와 일정은 추가 확인 중이다.
2026-11-02 팀 결정에 따라 이번 구현·시연에서는 제외하고 기획과 기존 비활성 초안만 유지한다.
버튼 저장·급정지 자동 저장·정차 후 음성 진술을 기본 흐름으로 사용한다.

## 비활성 설정

공모전에서 스키마 승인·활성화를 지원하는지 확인하기 전까지 사용하지 않는다. Manifest 수신기는 `enabled=false`, `exported=false`이며 코드의 `GleoRecordAction.ENABLED`도 `false`다. 스키마 초안은 `docs/gleo/schema.json`에만 보관하여 APK의 `assets/schema.json`으로 배포하지 않는다. 홈 음성 명령 안내는 숨겼고, 버튼 기록·진술 받아쓰기는 유지한다.

아래는 비활성화 전 구현·검증 내역이다. 재활성화하려면 승인 확인 후 스키마를 assets로 옮기고 수신기 설정·코드 가드를 함께 변경해야 한다.

## 구현 범위

차량 음성 비서가 명령을 해석하면 `SAVE_RECENT_DRIVE`를 앱으로 전달한다. 앱이 상시 마이크를 점유하거나 호출어를 직접 감지하지 않는다. 진술 받아쓰기용 마이크와도 별개다.

- 스키마 초안: 앱 이름 `순간기록`, 기능 `SAVE_RECENT_DRIVE`, 입력 인자 없음, `component: service`.
- 공식 규격에서 `service`는 `startService`가 아닌 `sendBroadcast`다. Manifest의 `GleoRecordReceiver`가 받아 비공개 `RecorderService`로 전달한다.
- 이미 기록 중이면 센서 연결과 버퍼를 재시작하지 않는다. 홈 버튼과 동일한 `Trigger.VOICE` 경로로 기록한다. 정차 중이면 바로 저장을 마무리하고, 주행 중이면 기존 규칙대로 이후 구간도 수집한다.
- 서비스가 꺼져 있었다면 이전 신호를 복구할 수 없다. 콜백도 이전 구간이 없다는 점을 명시한다. Android의 백그라운드 시작 제한 등으로 서비스를 시작하지 못하면 실패 응답을 보낸다.
- Gleo의 응답 제한 내에 **요청 접수 여부**를 반환하며 파일 저장 완료를 보장하는 표현은 쓰지 않는다. 응답에는 센서 상세값·진술·차량 번호를 담지 않는다.
- 외부에 공개된 진입점은 기록 요청 수신기다. 다른 앱도 같은 action을 전달할 수 있으므로 수신만으로 호출자를 인증한 것으로 간주하지 않는다. 삭제·조회·진술 수정 기능은 노출하지 않는다. 공식 문서에서 발신자 인증 방식이 추가되면 적용한다.

## 실제 음성 호출 활성화

공식 안내는 `assets/schema.json` 검토·승인 후 통합을 활성화한다고 명시한다. 설치 또는 부팅 시 스키마를 읽는 과정도 설명하지만 **APK 설치만으로 음성 호출 승인·활성화가 끝났다고 판단하면 안 된다.** 앱 버전을 바꿀 때 스키마의 `version`도 맞춘다.

승인된 환경에서 차량 음성 비서를 호출한 뒤 순간기록 앱의 최근 기록 저장을 요청해 검증한다. 실제 호출 문구·한국어 해석·백그라운드 명령 전달·응답 안내는 이 단계에서 확인한다. 앱이 호출어 없이 단독으로 “기록해”를 항상 듣는 기능은 아니다.

이번 작업에서는 외부 스키마 심사 요청이나 계정 등록을 제출하지 않았다.

## 로컬 검증

```bash
./gradlew :app:assembleDebug :core:test --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 실제 기기의 사용자 번호를 사용한다. 현재 에뮬레이터는 10.
adb shell am start --user 10 -n team.momentlog.app/.MainActivity
# 기록 서비스가 켜지고 신호가 쌓인 후 앱 화면을 닫는다.
adb shell input keyevent KEYCODE_HOME
adb shell am broadcast --user 10 -a SAVE_RECENT_DRIVE -p team.momentlog.app
adb logcat -s Momentlog:D
```

이 명령은 실제 기록을 생성한다. 에뮬레이터에서 백그라운드 명령 접수 로그, 정차 중 `VOICE` 기록 파일 생성, 기존 스냅샷 97개(약 11초 구간) 유지를 확인했다. 이 테스트는 **Gleo의 발화 해석·스키마 승인·실제 음성 응답을 검증하지 않는다.** 시작 후 쌓인 만큼만 저장되므로 테스트에서 3분 전체 구간이 있다고 주장하지 않는다.

## 공식 출처

- [Gleo Interface](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts)
- [App Schema 작성 규격](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability)
- [Intent 수신·콜백 예제](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples)
- [Callback 규격](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback)
- [스키마 전달·활성화](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema)
