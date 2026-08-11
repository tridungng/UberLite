# price-estimation-service

Price Estimation Service (PES) — ARCHITECTURE.md §2 and §8. Turns a trip request into a quote.

| | |
|---|---|
| Port | 8085 |
| Eureka id | `price-estimation-service` |
| State | none |
| Called by | `trip-service` |
| Calls | `route-service`, `time-estimation-service`, `surge-pricing-service`, `tax-tolls-service`, `discounts-promotions-service` |

## API

```
POST /price-estimates   body PriceEstimateRequestDto   → PriceQuoteDto
```

## The formula (paper Sec. 2, implemented as-is)

```
p = ((cd * d + ct * t) * s + cm) * η * (1 + T)
```

`cd` cost/distance, `d` distance, `ct` cost/time, `t` estimated time, `s` surge multiplier,
`cm` misc (tolls), `η` discount rate, `T` tax rate. `PricingCalculator` holds this and nothing else,
so the formula is unit-testable without any of the five network calls.

## The fan-out

This is the widest fan-out in the system — one quote is five downstream calls. `DependencyInvoker`
wraps each one so a failure surfaces as `DependencyFailedException` naming *which* dependency died,
and the controller maps that to `502`. A caller that gets a `502` learns the marketplace is fine and
the infrastructure is not; it must not cache or persist a partial quote.

Feign clients have no fallbacks. A fallback returning "surge 1.0" or "tax 0" would quietly sell a
ride at the wrong price, which is worse than not quoting at all.

## Configuration

```yaml
pricing:
  cost-per-km: 1.5          # cd, currency units per kilometre
  cost-per-minute: 0.3      # ct, currency units per minute
  default-detour-factor: 1.3  # Route Service returns no detourFactor at quote time
  region-id: default        # MVP is single-region (ARCHITECTURE.md §9)
  currency: USD
```

## Run

```bash
docker compose up -d discovery-server zipkin route-service time-estimation-service \
  surge-pricing-service tax-tolls-service discounts-promotions-service
./mvnw -pl price-estimation-service spring-boot:run
./mvnw -pl price-estimation-service test
```

`PriceEstimationIntegrationTest` runs the real Feign clients against `StubServer` from `common`'s
test-jar, so a `@FeignClient` path that disagrees with the downstream route fails the build rather
than the demo.



