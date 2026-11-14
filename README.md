# 일로오소 (ILLOOSO)

**목적지 근처에서 운전자와 동승자가 만나기 좋은 장소를 찾는 차량용 앱 프로토타입**

일로오소는 목적지 주변의 승하차·주차 후보를 지도에서 비교하고, 선택한 장소를 차량 내비게이션에 전달하는 경험을 탐색합니다. Pleos Connect용 Android 네이티브 앱으로 개발했습니다.

> 현재 평거동 일대를 대상으로 한 프로토타입입니다. 지도와 목적지 위치에는 OpenStreetMap 데이터를 사용하지만, 승하차 후보와 정차 적합성·운영 정보·후기는 예시입니다. 실제 정차 가능 여부나 현장 안전을 보장하지 않습니다.

![일로오소 지도 화면](verification/pleos-place-icons-2026-11-14.png)

## 주요 기능

- **목적지 선택** — 로컬 목적지 카탈로그를 검색하거나 Pleos 순정 내비게이션의 목적지를 가져옵니다.
- **장소 탐색** — 바로 태우기, 기다리기, 주차장 용도와 시간·거리 조건에 따라 주변 후보를 지도에서 비교합니다.
- **장소 정보와 후기** — 후보별 이용 정보와 예시 후기를 확인하고, 장소 저장 및 후기 작성 기능을 사용할 수 있습니다.
- **내비게이션 연결** — 선택한 장소의 좌표를 Pleos NaviHelper SDK에 전달하고 실제 응답을 확인합니다.
- **동승자 공유** — 기본 목적지는 QR 링크로 공유하고, 다른 목적지는 장소와 좌표를 텍스트로 전달합니다.
- **차량 UX 제한** — 주행 중이거나 차량의 조작 제한 상태를 확인할 수 없을 때 주요 조작을 제한합니다.

## 프로젝트 구성

| 경로 | 설명 | 기술 |
| --- | --- | --- |
| [`app/`](app/) | Pleos Connect용 네이티브 차량 앱 | Java 17, Android Views, Canvas, NaviHelper 3.0.5 |
| [`verification/`](verification/) | 기능별 검증 기록과 화면 캡처 | Android 단위 테스트, Pleos AVD |

## 시작하기

### Android 앱

**필요 환경**

- JDK 17 및 Android Studio
- Android SDK Platform 35, Android SDK Platform Tools
- Pleos Connect AVD 또는 호환되는 실행 환경
- Google Maven, Maven Central 및 Pleos Maven 저장소 접근

Android Studio에서 저장소의 최상위 디렉터리를 열고 Gradle JDK를 17로 설정합니다. Android SDK 경로는 각 환경의 `local.properties`에 설정합니다. 해당 파일은 Git에 포함되지 않습니다.

저장소 루트에서 빌드와 단위 테스트를 실행합니다.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

빌드가 완료되면 `app/build/outputs/apk/debug/app-debug.apk`가 생성됩니다. Android Studio 또는 `adb`로 Pleos AVD에 설치할 수 있습니다.

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

내비게이션 목적지 조회와 경로 설정에는 Pleos 환경과 해당 권한이 필요합니다. 앱에서 요청하는 내비게이션 조회·경로 설정 권한을 승인해야 합니다. SDK 연결과 응답 처리 방식은 [Pleos 브리지 문서](docs/PLEOS-BRIDGE.md)를 참고하세요.

## 데이터와 구현 범위

- 네이티브 앱은 OSM 지도 타일과 도로 형상을 사용합니다. 목적지와 후보 간 거리는 좌표로 계산한 **직선거리**이며 보행 경로 거리가 아닙니다.
- 장소 저장, 후기 및 제보는 기기에 보관합니다. 계정과 서버 동기화는 제공하지 않으며 앱 데이터 삭제 시 로컬 기록도 삭제됩니다.
- QR 공유는 2시간 유효한 장소 정보 스냅샷입니다. 실시간 위치 추적이나 다른 기기에서 링크 변경·취소 동기화는 지원하지 않습니다. 현재 공유 웹은 접근 권한에 따라 동승자가 열지 못할 수 있습니다.
- Pleos 내비게이션 연결과 로컬 미리보기는 별도 기능입니다. 경로 요청을 보낸 것만으로 안내 성공으로 처리하지 않습니다.

## 테스트와 검증

Android 단위 테스트는 장소·추천 조건·목적지·후기·지도 좌표 및 라벨 배치를 다룹니다. Pleos AVD에서의 기능별 확인 결과와 미검증 항목은 [검증 기록](verification/)에 남겨두었습니다.

## 문서

- [Pleos SDK 연동 상세](docs/PLEOS-BRIDGE.md)
- [프로젝트 기획서 (Notion)](https://app.notion.com/p/41388b2f14b180818a8ec3bc0078d4f0)

## 라이선스

이 저장소에는 아직 별도의 라이선스가 명시되어 있지 않습니다. 코드와 데이터의 재사용 조건은 저장소 소유자에게 확인해 주세요.
