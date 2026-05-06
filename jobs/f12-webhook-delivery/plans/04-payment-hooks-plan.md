# F12 Payment Hooks Plan

> 작성일: 2026-05-05
> 대상 브랜치: `feat/f12-webhook-payment-hooks` (`develop` 기준)
> 기준 문서: `jobs/f12-webhook-delivery/f12-webhook-delivery-plan.md`

## 1. Executive Summary

결제 승인 성공, 승인 실패, 취소 성공 시 웹훅 delivery를 생성하도록 결제 처리 경로에 훅을 연결한다. 이 단계는 F12에서 가장 조심해야 하는 부분이다.

## 2. Problem Statement

현재 결제 결과 저장 경로는 `PgOrchestrator.approve`, `UnifiedPaymentService.cancel`, `SandboxPaymentSimulator`에 나뉘어 있다. 웹훅 이벤트 생성을 단순 후처리로 붙이면 결제 저장 성공 후 delivery 생성 실패가 누락될 수 있다.

## 3. Proposed Solution

`WebhookPaymentEventPublisher`를 애플리케이션 서비스로 두고, 결제 결과가 저장된 직후 활성 endpoint를 조회해 delivery를 생성한다. 구현 중 트랜잭션을 작게 유지할 수 있으면 결제 저장과 delivery 생성을 같은 트랜잭션으로 묶는다. 기존 구조를 크게 바꿔야 하면 MVP 타협으로 best-effort를 명시하고 로그를 남긴다.

## 4. Feature Specification

- 승인 결과 `PaymentStatus.APPROVED`: `PAYMENT.APPROVED`
- 승인 결과 `PaymentStatus.FAILED`: `PAYMENT.FAILED`
- 취소 결과 `PaymentStatus.CANCELED`: `PAYMENT.CANCELED`
- sandbox/live 모두 동일 이벤트 생성
- 활성 endpoint가 없으면 조용히 no-op
- delivery payload는 같은 event id 기준으로 endpoint별 동일 JSON을 사용한다.

## 5. Technical Plan

- `application/service/WebhookPaymentEventPublisher.kt` 추가
- `UnifiedPaymentService.approve` 결과에 이벤트 생성 연결
- `UnifiedPaymentService.cancel` 결과에 이벤트 생성 연결
- sandbox 경로는 `UnifiedPaymentService`에서 결과를 받은 뒤 동일하게 처리한다.
- 필요하면 `PgOrchestrator` 내부 변경보다 외부 서비스의 트랜잭션 경계를 먼저 검토한다.
- 기존 `ApprovePaymentHandler`와 `CancelPaymentHandler`가 현재 사용 중인지 확인하고, 실제 요청 경로인 `UnifiedPaymentService` 중심으로 수정한다.
- 2026-05-05 기준 `PaymentController`는 `UnifiedPaymentService`를 사용한다. 따라서 handler류를 먼저 수정하지 않는다.
- `UnifiedPaymentService`는 승인/취소 결과에서 발행 대상 이벤트를 명시적으로 선택하고, `WebhookPaymentEventPublisher`는 전달받은 `WebhookEventType`으로 delivery를 생성한다.
- `WebhookPaymentEventPublisher`는 활성 endpoint가 없으면 no-op 한다.
- delivery payload에는 F12 원본 기획의 필드(`eventType`, `eventId`, `paymentId`, `merchantId`, `amount`, `currency`, `status`, `occurredAt`)를 포함한다.
- 동일 payment event에서 endpoint별 delivery는 같은 `eventId`와 같은 payload를 공유한다.
- delivery 생성은 HTTP 발송이 아니라 DB `PENDING` row 생성까지만 담당한다. 실제 발송은 02번 worker가 처리한다.

## 6. Success Metrics & Verification

- live 승인 성공 테스트에서 `PAYMENT.APPROVED` delivery가 생성된다.
- live 승인 실패 테스트에서 `PAYMENT.FAILED` delivery가 생성된다.
- sandbox 승인/취소 테스트에서도 delivery 생성이 검증된다.
- 취소 성공 테스트에서 `PAYMENT.CANCELED` delivery가 생성된다.
- endpoint가 없으면 결제 응답은 정상이고 delivery는 생성되지 않는다.
- 활성 endpoint가 2개면 같은 event id로 delivery 2개가 생성된다.
- 비활성 endpoint는 delivery 생성 대상에서 제외된다.

## 7. Risks & Mitigations

- 기존 원본 기획은 `ApprovePaymentHandler`, `CancelPaymentHandler`를 언급하지만 현재 컨트롤러는 `UnifiedPaymentService`를 사용한다. 실제 런타임 경로를 기준으로 수정한다.
- 결제 실패 이벤트는 "승인 시도 실패 후 Payment row가 저장된 경우"만 발행한다. 예외로 결제 row 자체가 생성되지 않은 경우는 F12 범위 밖이다.
- 트랜잭션 원자성을 억지로 맞추기 위해 orchestration 구조를 크게 바꾸면 회귀 위험이 커진다. 필요한 최소 변경으로 먼저 검증한다.
- 외부 PG 호출은 트랜잭션 밖에서 수행하고, 결제 저장과 delivery 생성만 `TransactionTemplate`으로 같은 트랜잭션에 묶는다. delivery 생성 실패 시 결제 저장도 rollback 대상이 되도록 예외를 삼키지 않는다.

## 8. Out Of Scope

- 결제 row가 없는 요청 검증 실패의 웹훅 발송
- provider별 세부 실패 이벤트
- 수동 이벤트 재생성

## 9. Commit Plan Draft

1. `feat(webhook): 결제 이벤트 delivery 생성 서비스 추가`
2. `feat(payment): 결제 승인 취소 경로에 웹훅 이벤트 연결`
3. `test(webhook): 결제 이벤트 delivery 생성 검증`
4. `test(payment): 결제 경로 웹훅 hook 검증`

## 10. Deep Review

이 단계는 기능적으로 작아 보여도 결제 핵심 경로를 건드린다. 성공 기준은 "웹훅이 만들어진다"만이 아니라 "웹훅 생성 실패가 결제 응답을 깨뜨리지 않는지", "실제 컨트롤러가 타는 경로에 붙었는지", "sandbox와 live가 같은 규칙을 따르는지"까지 포함한다. 구현 전에 테스트로 현재 호출 경로를 확인하고, 사용되지 않는 handler를 수정하는 실수를 피한다.
