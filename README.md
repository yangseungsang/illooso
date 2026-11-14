# 일로오소 (ILLOOSO)

**목적지 근처에서 운전자와 동승자가 만나기 좋은 장소를 찾는 차량용 앱 프로토타입**

일로오소는 목적지 주변의 승하차·주차 후보를 지도에서 비교하고, 선택한 장소를 차량 내비게이션에 전달하는 경험을 탐색합니다. Pleos Connect용 Android 앱을 중심으로 개발했으며, 동승자 공유 흐름을 위한 웹 프로토타입도 함께 포함합니다.

> 현재 평거동 일대를 대상으로 한 프로토타입입니다. 지도와 목적지 위치에는 OpenStreetMap 데이터를 사용하지만, 승하차 후보와 정차 적합성·운영 정보·후기는 예시입니다. 실제 정차 가능 여부나 현장 안전을 보장하지 않습니다.

![일로오소 지도 화면](pleos-app/verification/pleos-place-icons-2026-11-14.png)

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
| [`pleos-app/`](pleos-app/) | 주 실행 프로젝트. Pleos Connect용 네이티브 차량 앱 | Java 17, Android Views, Canvas, NaviHelper 3.0.5 |
| [`illooso/`](illooso/) | 초기 웹 프로토타입 및 동승자 공유 화면 | Next.js, React, TypeScript |
| [`pleos-app/verification/`](pleos-app/verification/) | 기능별 검증 기록과 화면 캡처 | Android 단위 테스트, Pleos AVD |

## 시작하기

### Android 앱

**필요 환경**

- JDK 17 및 Android Studio
- Android SDK Platform 35, Android SDK Platform Tools
- Pleos Connect AVD 또는 호환되는 실행 환경
- Google Maven, Maven Central 및 Pleos Maven 저장소 접근

Android Studio에서 `pleos-app` 디렉터리를 열고 Gradle JDK를 17로 설정합니다. Android SDK 경로는 각 환경의 `pleos-app/local.properties`에 설정합니다. 해당 파일은 Git에 포함되지 않습니다.

저장소 루트에서 빌드와 단위 테스트를 실행합니다.

```bash
cd pleos-app
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

빌드가 완료되면 `app/build/outputs/apk/debug/app-debug.apk`가 생성됩니다. Android Studio 또는 `adb`로 Pleos AVD에 설치할 수 있습니다.

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

내비게이션 목적지 조회와 경로 설정에는 Pleos 환경과 해당 권한이 필요합니다. 설치 및 권한에 관한 상세 내용은 [Android 앱 문서](pleos-app/README.md), SDK 연결과 응답 처리 방식은 [Pleos 브리지 문서](pleos-app/docs/PLEOS-BRIDGE.md)를 참고하세요.

### 웹 프로토타입

Node.js와 npm을 설치한 후 저장소 루트에서 실행합니다. 별도의 API 키나 서버 설정은 필요하지 않습니다.

```bash
cd illooso
npm install
npm run dev
```

브라우저에서 [http://localhost:3000](http://localhost:3000)을 엽니다. 정적 빌드, 도메인 테스트 및 타입 검사는 다음 명령으로 실행합니다.

```bash
npm run build
npm test
npm run typecheck
```

정적 빌드 결과는 `illooso/out/`에 생성됩니다. 웹의 지도와 내비게이션은 시뮬레이션이며, 네이티브 앱과 데이터가 동기화되지 않습니다. 자세한 내용은 [웹 문서](illooso/README.md)를 참고하세요.

## 데이터와 구현 범위

- 네이티브 앱은 OSM 지도 타일과 도로 형상을 사용합니다. 목적지와 후보 간 거리는 좌표로 계산한 **직선거리**이며 보행 경로 거리가 아닙니다.
- 장소 저장, 후기 및 제보는 기기에 보관합니다. 계정과 서버 동기화는 제공하지 않으며 앱 데이터 삭제 시 로컬 기록도 삭제됩니다.
- QR 공유는 2시간 유효한 장소 정보 스냅샷입니다. 실시간 위치 추적이나 다른 기기에서 링크 변경·취소 동기화는 지원하지 않습니다. 현재 공유 웹은 접근 권한에 따라 동승자가 열지 못할 수 있습니다.
- Pleos 내비게이션 연결과 로컬 미리보기는 별도 기능입니다. 경로 요청을 보낸 것만으로 안내 성공으로 처리하지 않습니다.

## 테스트와 검증

Android 단위 테스트는 장소·추천 조건·목적지·후기·지도 좌표 및 라벨 배치를 다룹니다. 웹 테스트는 추천 조건과 공유 데이터 등 도메인 로직을 검증합니다. Pleos AVD에서의 기능별 확인 결과와 미검증 항목은 [검증 기록](pleos-app/verification/)에 남겨두었습니다.

## 문서

- [Android 앱 개발 및 실행 안내](pleos-app/README.md)
- [Pleos SDK 연동 상세](pleos-app/docs/PLEOS-BRIDGE.md)
- [웹 프로토타입 안내](illooso/README.md)
- [프로젝트 기획서 (Notion)](https://app.notion.com/p/41388b2f14b180818a8ec3bc0078d4f0)

## 라이선스

이 저장소에는 아직 별도의 라이선스가 명시되어 있지 않습니다. 코드와 데이터의 재사용 조건은 저장소 소유자에게 확인해 주세요.
