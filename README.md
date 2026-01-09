# AR 건물 인식 데모 앱 (Android Native)

Kotlin + ARCore + ML Kit 기반의 실시간 건물 인식 및 정보 표시 앱입니다.

## 📱 기능

### 핵심 기능
- **실시간 객체 감지**: ML Kit Object Detection
- **AR 거리 측정**: ARCore Depth API (실제 미터 단위)
- **스마트 추적 시스템**:
  - 단기 추적 (IoU 기반) - 프레임 간 연속 추적
  - 장기 기억 (시그니처 기반) - 시야 이탈 후 복귀 시 동일 건물 인식

### 시그니처 시스템 (ObjectSignature)

각 건물을 고유하게 식별하기 위한 복합 특징:

```
┌─────────────────────────────────────────────────────┐
│ ObjectSignature                                      │
├─────────────────────────────────────────────────────┤
│ 📐 Shape 범위                                        │
│    - minAspectRatio ~ maxAspectRatio                │
│    - 다양한 각도에서의 형태 변화 허용                │
├─────────────────────────────────────────────────────┤
│ 📏 크기 범위                                         │
│    - minSize ~ maxSize (화면 대비 비율)             │
│    - 거리 변화에 따른 크기 변화 허용                 │
├─────────────────────────────────────────────────────┤
│ 📍 마지막 위치                                       │
│    - lastX, lastY (정규화 좌표 0~1)                 │
│    - 위치 연속성 기반 매칭                           │
├─────────────────────────────────────────────────────┤
│ 📏 실제 거리 범위 (ARCore Depth API)                │
│    - minRealDistance ~ maxRealDistance (미터)       │
│    - 사용자와 건물 간 실제 거리                     │
├─────────────────────────────────────────────────────┤
│ 🎨 평균 색상 (RGB)                                   │
│    - avgColorR, avgColorG, avgColorB                │
│    - 객체 고유 색상으로 구분                         │
└─────────────────────────────────────────────────────┘
```

### 유사도 계산 가중치

| 특징 | 가중치 | 설명 |
|------|--------|------|
| Shape 범위 | 25% | 가로세로 비율이 범위 내 |
| 크기 범위 | 20% | 화면 대비 크기가 범위 내 |
| 위치 거리 | 25% | 마지막 위치와의 거리 |
| 실제 거리 | 20% | ARCore 측정 거리가 범위 내 |
| 색상 유사도 | 10% | RGB 평균값 유사도 |

**최소 매칭 유사도: 45%**

## 🛠 기술 스택

- **Kotlin** - Android 네이티브 개발
- **ARCore 1.41.0** - Google AR 플랫폼
  - Depth API (실제 거리 측정)
  - Plane Detection
  - Hit Testing
- **ML Kit 17.0.1** - 객체 감지
  - Stream Mode (실시간)
  - Multiple Object Detection
- **Coroutines** - 비동기 처리
- **ViewBinding** - 뷰 바인딩

## 📂 프로젝트 구조

```
ARBuildingDemo/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/arbuildingdemo/
│   │   │   ├── models/
│   │   │   │   └── Building.kt          # 데이터 모델 (Building, ObjectSignature, ActiveTracker)
│   │   │   ├── services/
│   │   │   │   ├── ARCoreService.kt     # ARCore 깊이 측정
│   │   │   │   ├── BuildingTrackingService.kt  # 핵심 추적 로직
│   │   │   │   └── ObjectDetectionService.kt   # ML Kit 객체 감지
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt      # 스플래시 + 권한
│   │   │       ├── ARActivity.kt        # 메인 AR 화면
│   │   │       └── BuildingLabelView.kt # 커스텀 라벨 뷰
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml    # 스플래시 화면
│   │   │   │   └── activity_ar.xml      # AR 화면
│   │   │   ├── drawable/                # 배경, 버튼 등
│   │   │   └── values/
│   │   │       ├── strings.xml
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## 🚀 빌드 및 실행

### 요구사항
- Android Studio Hedgehog (2023.1.1) 이상
- JDK 17
- ARCore 지원 기기 (Android 7.0+)
- [ARCore 지원 기기 목록](https://developers.google.com/ar/devices)

### 빌드
```bash
# 프로젝트 열기
Android Studio에서 ARBuildingDemo 폴더 열기

# Gradle Sync
Sync Now 클릭

# 빌드
Build > Make Project

# 실행
Run > Run 'app'
```

### APK 생성
```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

## 📱 지원 기기

### 최소 요구사항
- Android 8.0 (API 26) 이상
- ARCore 지원 필수

### 권장 사양
- Depth API 지원 기기 (더 정확한 거리 측정)
  - Samsung Galaxy S20 이상
  - Google Pixel 4 이상
  - 기타 ToF/LiDAR 센서 탑재 기기

## ⚙️ 설정 커스터마이징

### 감지 신뢰도 조정
```kotlin
// ObjectDetectionService.kt
private const val CONFIDENCE_THRESHOLD = 0.6f // 60% 이상만 감지
```

### 매칭 유사도 조정
```kotlin
// BuildingTrackingService.kt
private const val MIN_SIMILARITY = 0.45f // 45% 이상 유사해야 매칭
private const val MIN_IOU_THRESHOLD = 0.2f // IoU 최소 임계값
private const val TRACKER_TIMEOUT_MS = 500L // 트래커 타임아웃
```

### 더미 건물 데이터 수정
```kotlin
// Building.kt
val dummyBuildings = listOf(
    Building(
        id = "custom_building",
        name = "커스텀 빌딩",
        distance = 100,
        colorName = "orange",
        description = "설명",
        address = "주소"
    ),
    // ...
)
```

## 🔧 확장 포인트

### 실제 건물 인식 모델 적용
1. TensorFlow Lite 커스텀 모델 학습
2. `ObjectDetectionService`에서 커스텀 모델 로드
3. 건물별 라벨로 매칭

### GPS 기반 건물 데이터베이스 연동
1. Room DB 또는 서버 API 연동
2. 현재 위치 기반 주변 건물 조회
3. 실제 건물 정보로 라벨 표시

### 건물 상세 정보 바텀시트
1. 라벨 클릭 시 상세 정보 표시
2. 길찾기, 전화, 웹사이트 등 액션 추가

## 📄 라이선스

MIT License
