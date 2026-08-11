# discounts-promotions-service

Discounts & Promotions Service (DPS) — ARCHITECTURE.md §2. Supplies the `η` term of the price
formula.

| | |
|---|---|
| Port | 8091 |
| Eureka id | `discounts-promotions-service` |
| State | Postgres `discountsdb` (host port 5435) |
| Called by | `price-estimation-service` |

## API

```
POST /discounts/evaluate   body DiscountEvaluationRequestDto {riderId, riderTripCount}
                           → DiscountQuoteDto {discountPct}
```

## How a rule is chosen

`promo_rules` rows carry a `condition_json`; `DiscountRuleFactory` turns each row into a
`DiscountRule`, and `DiscountEvaluator` applies them to a `DiscountContext`. Adding a promotion type
means adding a `DiscountRule` implementation and a row — not a branch in the evaluator.

Only `NewRiderTripCountRule` ships in the MVP ("first N rides: X% off").

## Schema (ARCHITECTURE.md §7)

```
promo_rules(id, description, discount_pct, condition_json)
```

## MVP simplification

Static rule table, no personalization model. `discounts-analytics-service` populates a
`promo_candidates` table intended for exactly that, but this service still prices from the live
`riderTripCount` on the request — reading the analytics table is a follow-up issue.

## Run

```bash
docker compose up -d discovery-server zipkin discounts-postgres
./mvnw -pl discounts-promotions-service spring-boot:run
./mvnw -pl discounts-promotions-service test
```

