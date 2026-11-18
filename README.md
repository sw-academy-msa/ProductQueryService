# ProductQueryService

STOCKDROP의 별도 Product Query 애플리케이션입니다.
Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.4 / Gradle 9.8.2를 사용합니다.

상품 목록·상세·옵션·표시 재고·판매 일정·확인된 FAQ·로그인한 구매자의 본인 문의를 조회합니다.
Kafka로 자기 product_query DB/스키마를 갱신하며 Command 원본 DB나 Account 테이블을 읽지 않습니다.
일반 Gateway(8000) → lb://product-query-service로 연결하고 기본 서비스 포트는 8082입니다.

## 로컬 실행

    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
    # 기존 일반 DB 컨테이너 stockdrop-account-postgres가 먼저 실행되어 있어야 합니다.
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Initialize-LocalQueryDatabase.ps1
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalQueryDatabase.ps1
    .\gradlew.bat bootRun

- 서버: http://localhost:8082
- 상태: http://localhost:8082/actuator/health
- Swagger(local): http://localhost:8082/swagger-ui.html
- 로컬 Query DB: 일반 PostgreSQL(5433)의 product_query DB / product_query 스키마
- Command DB(5434)와 분리합니다. 운영은 기존 RDS의 Query 전용 계정을 사용합니다.
- 초기화는 기존 DB·볼륨을 삭제하지 않고 비밀번호를 Git 제외 .env에 저장합니다. 재실행 시 기존 비밀번호를 바꾸지 않습니다.
- 이미지 URL은 기본 비활성입니다. 기존 Product S3Mock(9092) 사용 시 .env.example의 이미지 설정과 QUERY_IMAGES_ENABLED=true를 적용합니다.
- Kafka 소비는 로컬에서 기본 비활성입니다. 토픽 준비 후 QUERY_EVENTS_ENABLED=true와 접속 주소를 설정합니다.

## 구현 경계

Product Command에 Outbox 저장·발행이 추가되었습니다. Kafka 토픽을 준비하고 Command의 PRODUCT_OUTBOX_PUBLISHER_ENABLED=true 및 Query의 QUERY_EVENTS_ENABLED=true를 설정한 뒤 두 앱을 재시작하면 새 변경을 전파합니다. 기존 승인 상품의 자동 백필은 수행하지 않습니다.
발행 연동 전 실제 로컬 조회가 빈 목록인 것은 정상입니다. 임의 샘플 상품이나 원본 DB 직접 조회로 이를 우회하지 않습니다.
소비 계약과 중복/역전/재시도는 실제 Kafka·PostgreSQL 통합 테스트로 검증합니다.

- 공개 상품 GET은 로그인 없이 허용합니다. 잘못된 Bearer를 보내면 공개 경로라도 거절합니다.
- 본인 문의 GET은 BUYER JWT와 DB의 buyerId 조건으로 보호합니다.
- 구매·확보·상품 변경·문의 등록 POST는 제공하지 않습니다.
- 표시 재고는 지연될 수 있으며 실제 확보 가능 여부는 Command가 판단합니다.
- 전부 임시 확보된 상태와 결제 완료로 소진된 상태를 구분합니다.
- 시간은 UTC로 저장·반환하고 달력 날짜는 Asia/Seoul로 계산합니다.
- 확인된 FAQ는 공개하지만 다른 계정의 문의는 공개하지 않습니다.
- 출고/반품 주소·연락처·계좌·심사 사유는 공개 이벤트에서 제외합니다.
- AI Q&A·개인화 추천·알림·주문/결제는 해당 도메인 책임입니다.
- Redis 캐시는 아직 추가하지 않았습니다. 자기 PostgreSQL로 현재 시간 상태와 숨김·버전을 확인합니다.
- offset/DLT/소비 지연 모니터링과 조회 모델 재구축·백업/복원은 별도 운영 검증이 필요합니다.

## 테스트와 계약

    # Docker가 필요하며 기존 개발 DB 대신 격리된 PostgreSQL·KRaft Kafka를 사용합니다.
    .\gradlew.bat test bootJar

- [소비 이벤트 계약](contracts/README.md)
- openapi/product-query.openapi.json: 실제 컨트롤러에서 생성한 계약 스냅샷

개발 ddl-auto=update, 운영 validate를 유지합니다. 운영 DDL은 별도 검토·버전 관리 후 적용해야 합니다.
상세 docs와 AGENTS.md는 기존 규칙에 따라 Git에서 제외합니다.
Git add/commit/push와 AWS 변경은 수행하지 않습니다.

## HTTP 응답 DTO

상품 상세의 attributes·dimensions·faqs와 FAQ 조회는 HTTP 전용 DTO이며 Kafka 이벤트 타입과 분리합니다. 기존 필드는 유지합니다. GET /api/v1/categories 응답은 배열 대신 {"categories": [...]} 객체이므로 프런트는 categories 필드를 읽어야 합니다. 옵션·FAQ 목록은 응답 DTO의 배열을 유지합니다.


## 객체 생성 규칙

HTTP 응답 DTO는 record를 유지하고 Lombok 빌더로 필드명을 지정하여 생성합니다. 조회 모델과 멱등 이력은 기존 생성자에 빌더를 적용하여 초기 버전과 JPA INSERT 판정을 보존합니다. 기존 행의 상태 변경·잠금·이벤트 검증은 기존 서비스 트랜잭션에서 수행합니다. Kafka 수신 이벤트의 JSON 계약과 역직렬화 방식은 유지합니다.
Lombok은 Spring Boot가 관리하는 버전을 compileOnly·annotationProcessor로 사용하며 운영 실행 의존성에는 포함하지 않습니다. 빌더 문자열은 입력값을 출력하지 않습니다.

## 도메인 예외

상품 조회 업무 오류는 product/exception의 ProductQueryException·ProductQueryErrorCode로 표현하고 기존 COMMON_INVALID_REQUEST·COMMON_RESOURCE_NOT_FOUND 응답을 유지합니다. 공통 BusinessException을 상속하므로 기존 GlobalExceptionHandler가 처리합니다.
조회 모델 반영 오류는 projection/exception의 ProjectionException·ProjectionErrorCode로 구분합니다. 기존 InvalidProjectionEventException은 이 기반 타입을 상속하되 타입·패키지·메시지를 유지하여 Kafka의 영구 오류/DLT 분류를 보존합니다. 기반 예외 전체를 재시도 제외 대상으로 등록하지 않습니다. DB 장애·설정 오류·JDK 기능 오류 같은 기술적 실패는 기존 예외와 재시도·로그 경로를 유지합니다.

## 공통 성공 응답

Account와 같은 ApiResponse(success/code/message/data)를 반환합니다. 목록은 data.items, 상세는 data, 옵션·FAQ는 data 배열, 카테고리는 data.categories에서 읽습니다. HTTP 200·기존 오류 응답·DTO 변환·Kafka 계약은 유지합니다. Mapper는 추가하지 않습니다. 공통 성공 응답에는 Account와 같은 Cache-Control:no-store를 적용합니다.
