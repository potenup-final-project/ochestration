# Webhook URL Security Discussion

> 작성일: 2026-05-04
> 대상 작업: F12 webhook delivery
> 관련 계획: `plans/02-delivery-worker-plan.md`, `plans/03-endpoint-api-plan.md`

## 배경

웹훅은 가맹점이 등록한 URL로 우리 서버가 직접 HTTP 요청을 보내는 기능이다. 따라서 URL 등록/수정과 실제 발송 시점 모두 SSRF(Server-Side Request Forgery) 방어가 필요하다.

## 합의한 보안 방향

- `http`, `https` 스킴만 허용한다.
- URL에는 host가 반드시 있어야 한다.
- userinfo가 포함된 URL은 거절한다. 예: `https://user:pass@example.com`
- fragment가 포함된 URL은 거절한다.
- localhost, loopback, private IP, link-local IP, cloud metadata IP는 거절한다.
- 도메인은 DNS로 해석한 모든 A/AAAA 결과를 검사하고, 하나라도 차단 대상이면 거절한다.
- DNS 해석 실패는 등록/발송 모두 거절한다.
- DNS rebinding을 고려해 등록/수정 시점뿐 아니라 발송 직전에도 재검증한다.
- redirect는 기본적으로 따라가지 않는다. 추후 redirect를 허용할 경우 최종 목적지 URL도 동일하게 검증해야 한다.
- 응답 body는 읽지 않고 release해서 큰 응답으로 인한 메모리 사용을 막는다.
- 로그에는 signing secret, signature, payload 전문을 남기지 않는다.

## 02 Delivery Worker 반영 범위

- 발송 직전 URL 재검증을 추가한다.
- 재검증 실패는 설정 오류 또는 보안 차단으로 보고 `DEAD` 처리한다.
- HTTP client는 redirect follow를 명시적으로 켜지 않는다.
- HTTP client는 응답 body를 저장하지 않고 status code만 반환한다.

## 03 Endpoint API 반영 범위

- endpoint 등록/수정 시 동일한 URL validator를 사용한다.
- URL 검증 실패는 `WEBHOOK_ENDPOINT_URL_NOT_ALLOWED` errorCode로 응답한다.
- SSRF 차단 케이스를 controller/service 테스트에 포함한다.

## 남은 결정

- 운영에서 특정 사내망 URL을 허용해야 하는지 여부는 별도 정책으로 결정한다.
- 향후 allowlist 방식이 필요하면 merchant별 allowlist보다 전체 시스템 allowlist를 먼저 검토한다.
