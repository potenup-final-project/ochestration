# F12 Webhook Delivery Roadmap Plan

> 작성일: 2026-05-04
> 대상 브랜치: `feat/f12-webhook-delivery` (`develop` 기준)
> 기준 문서: `jobs/f12-webhook-delivery/f12-webhook-delivery-plan.md`

## 1. Executive Summary

F12는 결제 결과 이벤트를 가맹점 웹훅 엔드포인트로 비동기 발송하는 기능이다. 범위가 크므로 원본 기획을 그대로 한 번에 구현하지 않고, 리뷰와 커밋 경계가 명확한 4개 하위 계획으로 분리한다.

- `01-domain-persistence-plan.md`: 웹훅 도메인, 포트, DB/JPA 저장소
- `02-delivery-worker-plan.md`: HMAC 서명, HTTP 발송, 재시도 스케줄러
- `03-endpoint-api-plan.md`: 가맹점 웹훅 엔드포인트 관리 API
- `04-payment-hooks-plan.md`: 결제 승인/실패/취소 결과와 웹훅 이벤트 생성 연결

## 2. Problem Statement

원본 기획에는 도메인, 저장소, 워커, API, 결제 처리 훅이 모두 섞여 있다. 그대로 구현하면 변경 파일 수가 많고, Outbox 원자성 같은 핵심 결정이 흐려진다.

현재 코드 기준으로는 웹훅 관련 구현이 없고, 결제 결과는 `PgOrchestrator`, `UnifiedPaymentService`, `SandboxPaymentSimulator` 경로에서 저장된다. 따라서 결제 저장과 웹훅 delivery 생성의 트랜잭션 경계를 먼저 명확히 해야 한다.

## 3. Proposed Solution

F12 MVP는 `webhook_deliveries` 단일 테이블을 Outbox 겸 발송 작업 큐로 사용한다. 이벤트가 발생하면 활성 엔드포인트 수만큼 delivery row를 만들고, 스케줄러가 `PENDING` 또는 재시도 시간이 된 `FAILED` row를 조회해 HTTP POST를 발송한다.

원본 기획의 서명 요구사항은 `HMAC-SHA256(secret, eventId + "." + payload)`로 확정한다. 상세 설계 중 `payload`만 서명한다고 적힌 부분은 요구사항과 충돌하므로 따르지 않는다.

## 4. Feature Specification

- 이벤트 타입: `PAYMENT.APPROVED`, `PAYMENT.FAILED`, `PAYMENT.CANCELED`
- 페이로드 필드: `eventType`, `eventId`, `paymentId`, `merchantId`, `amount`, `currency`, `status`, `occurredAt`
- 서명 헤더: `X-Webhook-Signature: sha256=<hex>`
- 서명 대상: `eventId + "." + payload`
- delivery 상태: `PENDING`, `SENT`, `FAILED`, `DEAD`
- 재시도: 1초, 2초, 4초, 8초, 16초, 최대 5회
- 엔드포인트 제한: 가맹점당 최대 5개, 비활성 엔드포인트는 신규 delivery 생성 대상에서 제외

## 5. Technical Plan

1. 도메인/저장소를 먼저 만든다.
   - verify: 도메인 상태 전이 테스트와 JPA 저장소 테스트
2. 발송 워커를 만든다.
   - verify: signer 결정론 테스트와 성공/실패/DEAD 전이 서비스 테스트
3. 엔드포인트 CRUD API를 만든다.
   - verify: 컨트롤러 테스트와 예외 매핑 테스트
4. 결제 결과와 delivery 생성을 연결한다.
   - verify: 승인 성공/실패/취소 후 delivery 생성 테스트, 전체 테스트

## 6. Success Metrics & Verification

- `./gradlew test` 통과
- 결제 승인 성공 시 활성 엔드포인트별 `PAYMENT.APPROVED` delivery가 생성된다.
- 결제 승인 실패 시 `PAYMENT.FAILED` delivery가 생성된다.
- 결제 취소 성공 시 `PAYMENT.CANCELED` delivery가 생성된다.
- 2xx 응답은 `SENT`, 비 2xx/타임아웃은 `FAILED` 또는 `DEAD`로 저장된다.
- inactive endpoint에는 신규 delivery가 생성되지 않는다.

## 7. Risks & Mitigations

- F12 브랜치는 `develop` 기준으로 생성했다. F05 변경은 이미 `develop`에 머지된 상태를 전제로 한다.
- 결제 저장과 delivery 생성 원자성은 구현 위치에 따라 달라진다. `PaymentRepository.save()` 직후 같은 트랜잭션에서 생성하는 방향을 우선 검토하되, 기존 구조를 크게 흔들면 `04-payment-hooks-plan.md`에서 범위 축소를 명시한다.
- HTTP 발송 성공 후 상태 저장 실패 시 중복 발송 가능성이 있다. payload의 `eventId`와 delivery id 헤더를 제공해 수신 측 멱등성 처리가 가능하게 한다.

## 8. Out Of Scope

- 웹훅 발송 이력 조회 API
- 수동 재발송 API
- SQS/Kafka 연동
- signing secret 암호화/KMS 연동
- 수신자 SDK

## 9. Commit Plan Draft

1. `feat(webhook): 웹훅 도메인과 저장소 추가`
2. `feat(webhook): 웹훅 발송 워커 추가`
3. `feat(webhook): 웹훅 엔드포인트 관리 API 추가`
4. `feat(payment): 결제 결과 웹훅 이벤트 연결`
5. `test(webhook): 웹훅 발송 시나리오 검증`

## 10. Deep Review

이 계획의 핵심 검토 지점은 원자성이다. 원본 기획은 Outbox 원자성을 요구하지만, 현재 결제 저장은 여러 서비스와 시뮬레이터 경로에 흩어져 있다. 구현 중 단순한 후처리 호출로 끝내면 기능은 동작하지만 결제 저장 성공 후 delivery 생성 실패가 누락될 수 있다. 따라서 결제 저장 경로별로 트랜잭션 경계를 확인하고, 범위 안에서 해결 가능한 수준과 MVP 타협 지점을 코드 리뷰 전에 문서화한다.
