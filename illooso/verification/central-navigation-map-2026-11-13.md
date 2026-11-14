# 중앙 순정 내비게이션 지도 조사 — 2026-11-13

**지도 복구는 완료되지 않았으며 원인은 확정되지 않았다.** 앱의 지도 도식, 경로선 표시, SDK 연결 성공은 순정 내비게이션의 도로·지명 배경 지도 복구와 별개의 결과다.

## 현재 환경과 앱 변경

- Pleos Connect 이미지: `release_connect_v2.1.0`, Android 14 / API 34, macOS Apple Silicon.
- 이번 작업의 AVD는 Android Studio Running Devices에서 같은 사용자 데이터로 재시작되어 장치 ID가 `emulator-5586`에서 **`emulator-5554`**로 바뀌었다. 명령 실행 전 `adb devices`로 확인한다.
- 앱에 **중앙 내비게이션 지도 열기** 버튼과 `pleos.car.permission.NAVI_CUSTOM_MAP` 권한을 추가했다. 권한·연결·주차 정책을 확인한 뒤 `requestNavigationScreen(NavigationScreenRequest.Foreground.INSTANCE)`를 호출하도록 구현했다. 목적지나 경로를 변경하는 요청은 아니다.
- 버튼을 포함한 APK 빌드와 Android 도메인 JUnit 10개가 통과했고 APK를 설치했다. 실제 SDK 지도 화면 요청 진단이 통과했다. 이어서 Android Studio의 실제 에뮬레이터 화면에서 순정 내비가 열리는 것은 확인했으나 배경 지도는 공백이었다.

화면 요청의 권한은 [Pleos 공식 권한 문서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)에 명시되어 있다. NaviHelper는 기존 Navigation 앱에 요청을 전달하는 인터페이스다. [NaviHelper 공식 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)

## 공식 이미지 업데이트 여부

공식 현행 릴리스는 **v2.1.0, 2026-10-19**이다. 공개 릴리스 노트에는 이번 배경 지도 공백에 대한 수정·활성화 절차가 없다. [공식 릴리스 노트](https://document.pleos.ai/en/docs/pleos-only/release-note/emulator-release-note)

공식 Mac [저장소 XML](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml)의 패키지 revision은 `2`이지만, [배포 ZIP](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp_car_arm64_emulator.zip) 내부 `source.properties`는 `Pkg.Revision=1`이다. 설치본의 revision `1`만 보고 구버전이라고 판단할 수 없다. 저장소 URL의 출처는 [공식 설치 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)다.

원격 ZIP의 디렉터리·소형 파일을 HTTP Range로 읽고 설치본을 읽기 전용으로 비교했다. 아래 **파일 크기와 CRC32가 모두 일치**한다. ZIP 전체를 새로 설치하거나 시스템 이미지를 변경하지 않았다.

| 파일 | 크기(바이트) | 원격 ZIP 및 설치본 CRC32 |
| --- | ---: | --- |
| `source.properties` | 486 | `4c13d9c1` |
| `build.prop` | 6,867 | `ba5419e3` |
| `kernel-ranchu` | 12,784,248 | `27e8703b` |
| `ramdisk.img` | 1,540,073 | `0f4839b0` |
| `vendor.img` | 538,968,064 | `a8b7abb7` |
| `system.img` | 18,256,756,736 | `6af22a8e` |

배포 ZIP 메타데이터: 크기 `4,669,762,816`바이트, 게시된 SHA1 `24d28eb2f18eba5e87c861a7eb2bdccebb049419`, 서버 `Last-Modified: 2026-10-16 09:24:16 GMT`. 원격·로컬 `build.prop`는 10월 16일 빌드와 같은 `release_connect_v2.1.0` 식별자를 갖는다. 이 비교에서 새 공식 이미지라는 증거는 없으며, 동일 이미지 재다운로드를 새 복구 시도로 제안하지 않는다.

## 이미 수행한 그래픽·초기화 비교

[11월 9일 기존 진단](</Users/yangseungmin/Documents/ChatGPT/현차앱/outputs/map-verification-1.4.0/native-map-diagnosis.txt>)에는 host 렌더링, Vulkan 비활성화, 소프트웨어 렌더링, Android Emulator 37.2.13, 별도 새 AVD와 4GB 메모리 비교가 기록되어 있다. 기본 지도 공백은 지속되었고 일부 소프트웨어 모드는 검은 화면이나 다른 시스템 화면의 그래픽 실패를 일으켰다. 새 AVD에서도 재현되어 기존 앱 설치·사용자 데이터만으로 설명되지 않는다.

Apple Silicon의 software/SwiftShader 또는 Vulkan 비활성화는 [Android 공식 권고](https://developer.android.com/studio/run/emulator-troubleshooting#emulator_behaves_incorrectly_on_macos_in_hardware_rendering_mode)지만 이미 비교한 항목이다. 이 조사에서 추가 GPU 변경, 데이터 초기화, 시스템 패치, 인증 우회는 수행하지 않았다.

## 다른 환경의 재현 보고

공식 지원 저장소의 [Discussion #34](https://github.com/Pleos-Playground/pleos-playground-api/discussions/34)는 11월 8일 제기된 같은 증상이다. 11월 12일 다른 사용자가 **Windows 11·NVIDIA GPU·v2.1.0**에서도 현재 위치와 지명만 보이고 배경 지도가 비어 있다고 보고했다. 그 사용자는 네트워크 연결과 `caching tile` 로그가 정상이며 `swiftshader_indirect`도 해결하지 못했다고 설명했다.

11월 13일 확인 시 **운영자 답변과 확인된 우회책은 없었다.** 공식 지원 채널에 게시된 사용자 재현 보고이며, 제작사가 원인이나 미지원 여부를 인정한 공지는 아니다. Mac에만 한정된 문제라고 판단하기 어렵다는 정황으로만 사용한다.

## 로그의 한계와 다음 판정

- 이전 로그의 스타일 응답, 타일 캐시, 지도 이미지 공급·송신은 관련 구성요소의 활동 증거다. 실제 도로 픽셀이 화면에 표시되었다는 증거는 아니다.
- `Telemetry service not started, missing token`은 지도 타일 인증 실패를 확정하지 않는다. POI/충전사업자 조회의 HTTP 실패도 지도 공급 실패와 구분해야 한다.
- `GL_INVALID_VALUE`와 DrivingView의 GLES 오류만으로 순정 내비 지도 공백의 직접 원인을 확정하지 않는다.
- DNS 변경은 실제 DNS 실패가 확인될 때 검토할 수 있는 [Android 공식 진단 방법](https://developer.android.com/studio/run/emulator-troubleshooting#network-issues)이다. 정상 타일 수신 정황만 있는 상태에서는 네트워크 변경이 해결책이라고 주장할 수 없다.
- `requestNavigationScreen` 호출 뒤 앱 상태의 `requested`는 요청 전달만 의미한다. 현재 구현은 화면 가시성 확인 콜백을 받지 않으므로 이 상태를 지도 표시 성공으로 취급하지 않는다.

**복구 완료 기준:** 중앙 순정 내비게이션 화면에서 실제 도로·건물·지명 배경을 확인해야 한다. 이번 실제 API 호출 후에도 이 기준은 충족하지 못했다. 원본 로그에는 토큰·위치 등이 포함될 수 있어 외부 전달에는 정제된 증거만 사용한다.


## 실제 실행 결과

`emulator-5554`, 사용자 10, 같은 AVD의 Cold boot / Hardware / RAM 4GB 환경에서 다음 진단을 실행했다.

```bash
adb -s emulator-5554 shell am instrument --user 10 -w -e openMap true \
  ai.illooso.app/ai.illooso.app.ConnectionProbeInstrumentation
```

```text
PASS connected=true, policyKnown=true, drivingRestricted=false
 destinationResponded=true, destinationAvailable=false, route=idle
 openMap=true, mapRequested=true, visibleMapVerified=false, elapsedMs=143
```

`openMap` 옵션은 공개 NaviHelper API를 통해 화면 표시를 요청한다. 옵션이 없으면 기존 읽기 전용 진단을 유지한다. 위 PASS는 SDK 전달 검증의 결과다. ADB activity 상태에서도 `ai.umos.maps.android.navigation.app/.MainActivity`가 visible/topResumed였다.

이후 CUA로 Android Studio Running Devices의 실제 화면을 관찰했다. 중앙 순정 내비의 현재 위치 화살표, 검색창, 2D 버튼, `수정구 시흥동` 표시는 보였으나 **도로·건물 배경은 공백**이었다. 화면 전환 성공과 배경 지도 복구 실패를 따로 기록한다. 실제 경로 탐색·주행은 이번에 요청하지 않았다.

앱 홈의 새 지도 열기 버튼 렌더링은 확인했다. CUA의 분리 창 포커스 제약 때문에 버튼 터치 E2E는 완료하지 못했고, 같은 브리지 API를 기기 통합 진단으로 검증했다. 앱 내부 도식 지도는 변경하지 않았다. 전체 네이티브 사용자 흐름 QA는 별도 작업이다.
