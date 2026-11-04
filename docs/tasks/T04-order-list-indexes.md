# T04. Індекси БД для списку замовлень

| Поле | Значення |
|------|----------|
| Пріоритет | P1 (хвиля 1) |
| Розмір | S |
| Статус | Done |
| Пов'язано | T01 |

## Проблема
У `V1__baseline.sql` для `"order"` є лише `(tenant_id)` та `(tenant_id, session_id)`. Основний запит списку
(`tenant_id` + `status` + сортування за `created_at DESC`) і лічильники метрик виконують сортування/сканування по всьому tenant.

## Обсяг
- Міграція `V2__order_list_indexes.sql`:
  `CREATE INDEX idx_order_tenant_created ON "order" (tenant_id, created_at DESC);`
  `CREATE INDEX idx_order_tenant_status_created ON "order" (tenant_id, status, created_at DESC);`
- Перевірити `EXPLAIN ANALYZE` на ≥ 100k замовлень (скрипт у `scripts/`), зафіксувати результат у PR.
- Індекс на `order_item(order_id)` (якщо відсутній) для пошуку по позиціях.

## Критерії приймання
- [x] Flyway застосовує V2 на свіжій і на наявній БД (`ddl-auto: validate` проходить). _Перевірено на Postgres 16: чиста БД (V1+V2) і БД без Flyway (baseline на V1, потім V2)._
- [x] План запиту списку використовує новий індекс; час відповіді на 100k замовлень < 100 мс.
