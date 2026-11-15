# N딩크레딧

팀 **부릉부릉**의 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

| 앱 | 한 줄 소개 | 소스 | 서명된 release APK |
| --- | --- | --- | --- |
| **N딩크레딧** (런처 표시명 `N딩 크레딧`) | Pleos Connect의 위치·차량 신호로 여행 경로와 방문을 기록하고, 여행이 끝나면 크레딧과 기록을 보여 주는 앱입니다. | [original/](original/) | [부릉부릉_N딩크레딧_release.apk](https://github.com/shkim99stat/Hyundai_Pleos_NdingCredit/releases/tag/submission-2026) |
| **N딩크레딧_시연** (런처 표시명 `N딩 크레딧 시연`) | 앱에 포함된 동해안 1박 2일 경로로 여행 기록, 장소 확인, 크레딧, 이동비 추정을 재현하는 시연 앱입니다. | [demo/](demo/) | [부릉부릉_N딩크레딧_시연_release.apk · GitHub Release](https://github.com/shkim99stat/Hyundai_Pleos_NdingCredit/releases/tag/submission-2026) |

두 APK는 각각 GitHub의 일반 Git 파일 한도인 100 MiB를 초과하여 같은 Release에 첨부했습니다. [공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission) · [GitHub 파일 용량 제한](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)

## 개발·테스트 환경

- **Pleos Connect 에뮬레이터 v2.1.0** · Android 14 Automotive (API 34) · x86_64
- **가로 2560 × 1440 px**, 물리 화면 밀도 **160 dpi**. 두 앱의 메인 Activity는 가로 방향으로 선언되어 있습니다.
- 두 앱을 설치한 뒤 정차 상태에서 실행합니다. Pleos에서 주행 제한이 활성화되면 여행 기록 화면의 조작을 막고, 다른 화면에는 주행 중 조작 안내를 표시합니다.
- 원본의 차량·위치 기록에는 Pleos 에뮬레이터의 위치 서비스와 차량 신호가 필요합니다. 최초 동의 후 정확한 위치 및 Pleos 위치 권한을 허용하고, 시스템 위치를 켭니다. 속도·기어·도어 신호를 사용할 수 있는 상태에서 출발·정차를 시험해 주세요. 착석 인원과 전기 사용량은 각각 좌석과 에너지 조회 권한 및 지원 신호가 있을 때 사용할 수 있습니다.
- 시연 앱은 앱 안의 시간·경로·후보를 재생합니다. 새 여행의 초기 인원은 4명이며, 경로 재생에 차량 값이나 에뮬레이터 위치를 변경할 필요가 없습니다.
- 배경 지도는 MapLibre와 OpenFreeMap을 사용하므로 인터넷 연결이 필요합니다. 제출 APK의 Kakao 키는 비워 두었습니다. 원본의 주변 장소 검색과 도로명 주소 조회에는 Kakao 설정이 필요하며, 정차 장소 이름은 직접 입력할 수 있습니다. 시연의 고정 후보와 경로 재생에는 Kakao 키가 필요하지 않습니다. [Pleos 개발 환경 안내](https://appdevchallenge.auto-hmg.io/guide/development) · [OpenFreeMap](https://openfreemap.org/) · [Kakao Maps JavaScript 설정](https://apis.map.kakao.com/web/guide/)

## APK 설치 및 실행

[두 APK가 들어 있는 ZIP 다운로드](https://github.com/shkim99stat/Hyundai_Pleos_NdingCredit/releases/download/submission-2026/submission-apks.zip)에서 ZIP을 내려받아 압축을 풉니다. GitHub가 개별 첨부의 한글 파일명을 바꾸므로 ZIP에는 제출 파일명을 그대로 보존했습니다. Pleos 에뮬레이터를 켠 상태에서 압축을 푼 폴더의 터미널에서 실행합니다.

```powershell
adb devices
adb install "부릉부릉_N딩크레딧_release.apk"
adb install "부릉부릉_N딩크레딧_시연_release.apk"
```

앱 목록에서 **N딩 크레딧** 또는 **N딩 크레딧 시연**을 엽니다. 패키지 ID는 각각 `com.example.pleos_endingcredit`와 `com.example.pleos_endingcredit.demo`이므로 함께 설치할 수 있습니다. 기존 개발 APK와 서명이 다르면 깨끗한 에뮬레이터에서 설치하세요.

### 원본 앱

1. 두 필수 약관에 동의하고 **동의 후 시작**을 누릅니다. 위치 권한 요청을 처리하면 홈으로 이동합니다.
2. **여행 준비하기**를 누릅니다. 크루 선택에서 **여행 시작하기**, 여행 정보에서 **기록 시작하기**를 누릅니다.
3. 위치 수신과 차량 신호를 확인합니다. P에서 벗어나 주행한 뒤, P에서 탑승자 도어를 열면 정차 후보가 생깁니다. 다시 주행을 시작하면 그 정차가 방문 목록에 반영됩니다. 정차 상태에서 방문 장소를 확인하거나 이름을 직접 입력할 수 있습니다.
4. 정차하고 **여행 끝내기**를 누릅니다. 영상 없이 진행하려면 **영상 없이 만들기**를 누릅니다. QR이 표시되면 같은 로컬 네트워크에서 에뮬레이터에 접속할 수 있는 휴대전화로 영상을 보낼 수 있습니다.
5. 크레딧을 본 뒤 여행 결과와 방문 목록을 확인합니다. 홈의 **이전 여행 다시보기**에서 기록을 다시 열 수 있습니다.

위치 원본과 속도 관측은 SQLite에, 지도 미리보기 및 방문 편집은 별도 여행 기록에 저장합니다. 경로와 이동거리는 위치를 바탕으로 구성하고, 주행 시간과 방문 판정에 기어·속도·도어 신호를 사용합니다. 차량은 조회만 합니다. 원본의 실기록 결과에서 수집하지 않은 통행료·주차비 및 이동비 합계는 값이 비어 있으며, 결제는 연결되어 있지 않습니다. [신호 수집](original/app/src/main/java/com/example/pleos_endingcredit/TripVehicleSignals.kt) · [여행 기록 계산](original/app/src/main/java/com/example/pleos_endingcredit/TripRecording.kt) · [원본 샘플 저장](original/app/src/main/java/com/example/pleos_endingcredit/TripSampleStore.kt) · [이동비 화면](original/app/src/main/java/com/example/pleos_endingcredit/D1_1CostsView.kt)

### 시연 앱

1. 두 필수 약관에 동의하고 **동의 후 시작**을 누릅니다. **여행 준비하기** → **여행 시작하기** → **기록 시작하기** 순서로 들어갑니다.
2. 기록 화면 왼쪽 아래의 **시연**을 엽니다. 각 구간이 끝나면 다음 구간으로 출발합니다. 홍천휴게소와 횡성휴게소에서는 **장소 확인**으로 후보를 선택합니다. 다른 여섯 곳은 도착 시 자동 기록합니다.
3. 속초관광수산시장에서 **2일차 시작**, 경포해변에서 **귀경길로 출발**, 덕평자연휴게소에서 **밤길로 서울 출발**을 누릅니다. 서울 도착 뒤 **여행 끝내기**를 누르고 종료를 확인합니다.
4. 한 번에 결과를 확인하려면 시연 메뉴의 **시연 종료**를 누릅니다. 남은 구간과 미확인 방문을 시나리오로 채운 뒤 영상 선택 화면으로 이동합니다. **영상 없이 만들기**로 크레딧을 재생할 수 있습니다.
5. 결과 화면에서 **이동비 확인하기**를 누릅니다. 시연 이동비는 주행 거리, 기준 전비 5.0 km/kWh, 300원/kWh, 시연 통행료 8,600원, 주차비 0원으로 계산합니다. 나누는 인원 변경은 이동비 QR에도 반영됩니다.

새 시연 여행은 2026년 11월 14일부터 15일까지 진행됩니다. 서울 → 속초 → 강릉 → 서울 경로에 9개 이동 구간과 8개 방문 장소가 있습니다. 총 504.4 km를 7시간 16분 동안 주행하며, 18시부터 다음 날 06시까지를 기준으로 집계한 밤길 주행은 120.6 km, 1시간 44분입니다.

시연 메뉴에서 일시정지, 정차로 건너뛰기, 장면 선택, 다시 시작을 사용할 수 있습니다. [시나리오](demo/app/src/main/java/com/example/pleos_endingcredit/DemoScenario.kt) · [재생 상태](demo/app/src/main/java/com/example/pleos_endingcredit/DemoPlayback.kt) · [조작 메뉴](demo/app/src/main/java/com/example/pleos_endingcredit/DemoControlsView.kt) · [시연 이동비 계산](demo/app/src/main/java/com/example/pleos_endingcredit/D1_1CostsView.kt)

## 소스 빌드

Android Studio에서 `original/`과 `demo/`를 각각 독립 프로젝트로 엽니다. Gradle Wrapper **9.6.0**, Android Gradle Plugin **9.5.1**, Android SDK **API 37**을 사용합니다. 원본은 Pleos Vehicle **2.3.2**와 FusedLocation **1.1.3**에 의존하며 두 앱 모두 SpeechToText·TextToSpeech **3.3.1**을 빌드에 선언합니다.

각 프로젝트의 루트에서 다음 명령을 실행합니다. 생성된 `app/build/outputs/apk/release/app-release-unsigned.apk`를 직접 설치하려면 서명해야 합니다.

```powershell
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest
```

원본 장소 검색을 개발 환경에서 시험하려면 [설정 예시](original/kakao-map.properties.example)에 따라 로컬 `KAKAO_MAP_JS_KEY`와 `KAKAO_MAP_ORIGIN`을 설정하고 빌드합니다. 해당 origin을 Kakao JavaScript SDK 도메인에 등록합니다. 제출본은 Pleos 에뮬레이터에서 초기 실행 오류가 발생한 release 최적화를 끈 상태로 빌드했고, 별도 제출용 키로 서명했습니다. [Android APK 서명 안내](https://developer.android.com/tools/apksigner)

## 데이터·라이선스

- 지도와 지형 데이터 출처는 [지도 자산 출처](original/app/src/main/assets/b3/flat-map/sources.txt) 및 [지형 자산 출처](original/app/src/main/assets/credit-terrain/credit-terrain-sources.txt)에 적혀 있습니다.
- 시연 경로 데이터 출처는 [FOSSGIS OSRM](https://routing.openstreetmap.de/about.html)과 [OpenStreetMap 기여자](https://www.openstreetmap.org/copyright)입니다.
- 포함된 글꼴과 MapLibre 라이선스는 각 앱의 `app/src/main/assets/licenses/`에 있습니다.
