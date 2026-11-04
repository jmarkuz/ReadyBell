# T05. Transactional outbox для OrderReady (BACKLOG R1)

| Поле | Значення |
|------|----------|
| Пріоритет | P1 (хвиля 2) |
| Розмір | L |
| Статус | Todo |

## Проблема
Замовлення зберігається, а потім `OrderReady` публікується в процесі. Якщо сервіс падає між цими кроками — клієнт ніколи не
отримає push «замовлення готове». Це головна обіцянка продукту.

## Мета
Подія записується в ту саму транзакцію, що й зміна статусу, і доставляється принаймні один раз.

## Обсяг
- Таблиця `outbox_event` (id, tenant_id, type, payload JSON, created_at, processed_at, attempts) — міграція Flyway.
- `DomainEventPublisher` (порт) пише в outbox замість прямого `ApplicationEventPublisher` для `OrderReady` та `OrderStatusUpdated`.
- Relay: `@Scheduled` (або `LISTEN/NOTIFY`) читає необроблені записи `FOR UPDATE SKIP LOCKED`, викликає наявні обробники, ставить `processed_at`.
- Ідемпотентність `OrderReadyHandler` (за `orderId`) — дублікати не створюють двох push.
- Очищення оброблених записів (housekeeping), метрики `outbox.pending`, `outbox.failed`.
- Узгодити з R3 (один вузол для housekeeping) — relay безпечний на кількох вузлах завдяки `SKIP LOCKED`.

## Критерії приймання
- [ ] Тест: зміна статусу + примусовий збій після commit до relay → після рестарту push відправлено один раз.
- [ ] Тест: паралельні relay не обробляють одну подію двічі.
- [ ] ArchUnit-правила не порушені (`domain`/`application` без Spring).
- [ ] `docs/architecture` і `REVIEW.md` оновлені.
