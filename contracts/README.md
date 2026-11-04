# Product Query 소비 계약 v1

이 계약은 Query 소비와 Product Command Outbox 발행의 공통 기준입니다. 원본 변경과 Outbox 저장은 같은 Command 트랜잭션으로 처리하며 발행은 별도 설정으로 활성화합니다.
Query가 원본 DB를 읽거나 공개 HTTP 쓰기 API로 모델을 임의 갱신하는 경로는 없습니다.

## 토픽과 envelope

- 공개 상품: stockdrop.product.public.v1
- 본인 문의: stockdrop.product.inquiries.v1
- DLT: 원본 토픽 이름 뒤 .DLT, 원본 이상 파티션 수 필요
- Kafka key: 두 토픽 모두 상품 UUID 문자열
- 기본 소비 그룹: product-query-v1, 같은 앱 복제본은 같은 그룹 사용
- AI·Notification 등 다른 목적은 별도 소비 그룹 사용
- 값: UTF-8 JSON, DTO ProjectionEvent가 실행 가능한 계약의 기준

공통 필드는 eventId(UUID), schemaVersion(1), type, aggregateId(UUID), aggregateVersion(0 이상), occurredAt(UTC Instant)입니다.
PRODUCT_UPSERT는 product만, INQUIRY_UPSERT는 inquiry만, PRODUCT_UNPUBLISHED는 두 데이터 필드 없이 보냅니다.
상품 aggregateId는 상품 ID, 문의 aggregateId는 문의 ID입니다.
공개 스냅샷 status는 APPROVED만 허용하고 알 수 없는 필드·스키마·타입은 DLT로 분리합니다.
비공개 주소·연락처·계좌·심사 사유를 공개 이벤트에 넣지 않습니다. 문의 토픽은 구매자 ID와 본문을 포함하므로 별도 ACL이 필요합니다.
이벤트 원문과 토큰을 애플리케이션 로그로 출력하지 않습니다.

## 버전과 멱등

**상품 aggregateVersion은 Command의 기존 JPA @Version을 그대로 사용하면 안 됩니다.**
현재 Command의 재고·이미지·FAQ 변경 모두가 같은 JPA 버전을 증가시키는 구조는 아닙니다.
후속 발행은 공개 조회에 영향을 주는 모든 변경의 별도 단조 증가 projectionVersion을 같은 로컬 트랜잭션에서 관리해야 합니다.
상품 승인·옵션·재고 확보/확정/해제·이미지·확인 FAQ 변경의 일관된 공개 스냅샷과 버전을 Outbox에 저장합니다.
문의는 자체 생성/답변 버전을 사용하며 상품 버전과 섞지 않습니다.
동일 eventId는 재발행해도 내용·발생 시각을 바꾸지 않습니다. 같은 ID의 다른 내용과 같은 버전의 다른 스냅샷은 거부합니다.
조회 모델과 processed_events는 같은 Query 트랜잭션으로 저장합니다.
최초 INSERT도 product/inquiry별 PostgreSQL advisory transaction lock으로 직렬화합니다.
멱등 UUID는 최초 INSERT로 저장해 다른 aggregate에서 동시에 재사용한 eventId가 기존 이력을 merge로 덮어쓰지 못하게 합니다.
오래된 버전은 이력만 남기고 최신 모델을 덮어쓰지 않습니다. 비공개도 tombstone 버전을 남겨 늦은 승인 이벤트의 부활을 막습니다.
tombstone과 이벤트 이력은 재생 범위 검토 없이 임의 삭제하지 않습니다.

## 스냅샷

상품은 상품명/설명/브랜드·판매처/카테고리/참고 URL/가격/배송 조건/구매 상한/안내/일정/상세 항목/옵션 구조/이미지/확인 FAQ를 포함합니다.
옵션의 initialQuantity/reservedQuantity/confirmedQuantity를 구분하고 available은 표시용으로 계산합니다.
임시 확보를 판매 완료에 더하지 않습니다. 조회 가능 수량이 0이어도 결제가 완료되지 않았다면 완판으로 표시하지 않습니다.
이미지는 서버 전용 product-images/{상품UUID}/{이미지UUID} 키와 검증된 메타데이터만 받습니다. 업로드 URL은 전달하지 않습니다.
soldOutAt은 선택적 원본 사실입니다. 없으면 완판 소요 시간이나 참여자 수를 임의 생성하지 않습니다.
문의는 productId/buyerId/question/answer/createdAt/answeredAt입니다. 구매자는 자기 문의만 조회할 수 있습니다.

## 장애와 재처리

DB 장애는 무제한 backoff 재시도하며 복구 전에 해당 레코드를 성공 처리하지 않습니다.
영구 오류는 DLT로 보내고 발행 성공 응답을 확인해야 원본 소비를 진행합니다. DLT 발행 실패도 재시도 대상입니다.
운영자의 DLT 조사·교정/재발행·알림 절차는 별도 과제입니다.
Outbox 발행 완료는 Query 반영 완료가 아닙니다. 조회 지연만으로 구매/결제 성공을 판단하지 않습니다.
Kafka 디스크 유실·보존 기간 만료 후 재구축은 별도 스냅샷/백업/재발행 계약이 필요합니다.
예시 JSON은 통합 테스트가 build/generated/events에 합성 데이터로 생성합니다. 실제 토큰·계정 정보는 예시에 넣지 않습니다.
