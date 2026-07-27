# Coupon Service — serwis kuponów rabatowych

REST-owy serwis do zarządzania kuponami rabatowymi: tworzenie kuponów oraz rejestrowanie ich użycia
z limitem liczby użyć, ograniczeniem geograficznym (po adresie IP) i opcjonalną zasadą „jeden
użytkownik = jedno użycie". Zaprojektowany z myślą o poprawnym działaniu w **wielowątkowym
środowisku produkcyjnym**.

> Zakres funkcjonalny i wymagania podsumowano w sekcji [Wymagania](#wymagania).

---

## Spis treści

- [Stack technologiczny](#stack-technologiczny)
- [Szybki start (Docker Compose)](#szybki-start-docker-compose)
- [API](#api)
  - [Dokumentacja interaktywna (OpenAPI / Swagger UI)](#dokumentacja-interaktywna-openapi--swagger-ui)
- [Reguły biznesowe i kody błędów](#reguły-biznesowe-i-kody-błędów)
- [Architektura](#architektura)
- [Kluczowe decyzje projektowe](#kluczowe-decyzje-projektowe)
- [Konfiguracja](#konfiguracja)
- [Budowanie i uruchamianie lokalnie](#budowanie-i-uruchamianie-lokalnie)
- [Testy](#testy)
- [Wymagania](#wymagania)

---

## Stack technologiczny

| Obszar | Technologia |
| --- | --- |
| Język | Java 21 |
| Framework | Spring Boot 3.3 (Spring Web, Spring Data JPA, Validation, Actuator) |
| Dokumentacja API | springdoc-openapi (OpenAPI 3 + Swagger UI) |
| Persystencja | JPA / Hibernate + PostgreSQL 16 |
| Migracje schematu | Flyway |
| Build | Maven (z dołączonym Maven Wrapper `./mvnw`) |
| Ograniczenie boilerplate | Lombok (`@Getter`, `@NoArgsConstructor` na encjach) + rekordy na DTO |
| Konteneryzacja | Docker (multi-stage build) + Docker Compose |
| Testy | JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL) |
| Geolokalizacja IP | [ip-api.com](http://ip-api.com) (darmowe, bez klucza) |

---

## Szybki start (Docker Compose)

Wymagany tylko **Docker** i **Docker Compose** — kod jest budowany wewnątrz obrazu, nie trzeba mieć
lokalnie Javy ani Mavena. Compose uruchamia bazę PostgreSQL, buduje projekt i startuje aplikację.

```bash
docker compose up --build
```

Po starcie:

- API: `http://localhost:8080/api/v1/coupons`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`

Zatrzymanie i usunięcie danych:

```bash
docker compose down -v
```

> **Zajęty port 8080?** Host-owy port jest konfigurowalny: `APP_PORT=18080 docker compose up --build`
> wystawi aplikację na `http://localhost:18080`.

### Przykład użycia (end-to-end)

```bash
# 1. Utworzenie kuponu (2 użycia, tylko Polska)
curl -X POST http://localhost:8080/api/v1/coupons \
  -H 'Content-Type: application/json' \
  -d '{"code":"WIOSNA","maxUses":2,"country":"PL"}'

# 2. Użycie kuponu — wielkość znaków bez znaczenia (wiosna == WIOSNA).
#    Nagłówek X-Country-Code pozwala testować regułę kraju bez publicznego IP.
curl -X POST http://localhost:8080/api/v1/coupons/wiosna/redemptions \
  -H 'Content-Type: application/json' \
  -H 'X-Country-Code: PL' \
  -d '{"userId":"user-1"}'
```

---

## API

### Dokumentacja interaktywna (OpenAPI / Swagger UI)

Po uruchomieniu aplikacji dostępna jest wygenerowana z kodu specyfikacja OpenAPI 3 (springdoc):

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **Specyfikacja JSON:** `http://localhost:8080/v3/api-docs`

Opisy endpointów, parametrów, modeli i kodów błędów są zdefiniowane adnotacjami przy kodzie
(`@Operation`, `@ApiResponse`, `@Schema`), więc dokumentacja nie rozjeżdża się ze źródłem.

### Ścieżki

Bazowa ścieżka: `/api/v1/coupons`

### 1. Utworzenie kuponu

`POST /api/v1/coupons`

```json
{
  "code": "WIOSNA",
  "maxUses": 100,
  "country": "PL"
}
```

| Pole | Reguły walidacji |
| --- | --- |
| `code` | wymagane, niepuste, maks. 64 znaki (normalizowane do wielkich liter) |
| `maxUses` | liczba całkowita ≥ 1 |
| `country` | kod ISO 3166-1 alpha-2 (np. `PL`, `DE`) |

**`201 Created`**

```json
{
  "id": 1,
  "code": "WIOSNA",
  "createdAt": "2026-07-27T20:53:20.340456053Z",
  "maxUses": 100,
  "currentUses": 0,
  "country": "PL"
}
```

### 2. Rejestracja użycia kuponu

`POST /api/v1/coupons/{code}/redemptions`

```json
{
  "userId": "user-1"
}
```

- `userId` jest **opcjonalne**. Gdy jest podane — obowiązuje reguła „jeden użytkownik = jedno użycie".
  Gdy go brak — kupon jest limitowany wyłącznie maksymalną liczbą użyć. Body może być też puste (`{}`).
- Kraj żądania ustalany jest z adresu IP (nagłówek `X-Forwarded-For` lub adres zdalny), a na potrzeby
  testów można go nadpisać nagłówkiem `X-Country-Code` (patrz [Konfiguracja](#konfiguracja)).

**`200 OK`**

```json
{
  "code": "WIOSNA",
  "message": "Coupon redeemed successfully.",
  "currentUses": 1,
  "maxUses": 100,
  "remainingUses": 99
}
```

### Format błędu

Wszystkie błędy mają spójną kopertę z maszynowo-czytelnym polem `code`:

```json
{
  "timestamp": "2026-07-27T20:53:20.491378763Z",
  "status": 409,
  "code": "COUPON_EXHAUSTED",
  "message": "Coupon 'WIOSNA' has reached its maximum number of uses.",
  "path": "/api/v1/coupons/WIOSNA/redemptions",
  "details": []
}
```

---

## Reguły biznesowe i kody błędów

| Sytuacja | HTTP | `code` |
| --- | --- | --- |
| Poprawne utworzenie kuponu | `201` | — |
| Poprawne użycie kuponu | `200` | — |
| Kod kuponu już istnieje (case-insensitive) | `409` | `DUPLICATE_CODE` |
| Kupon nie istnieje | `404` | `COUPON_NOT_FOUND` |
| Osiągnięto maksymalną liczbę użyć | `409` | `COUPON_EXHAUSTED` |
| Żądanie z niedozwolonego kraju | `403` | `COUNTRY_NOT_ALLOWED` |
| Użytkownik już wykorzystał kupon | `409` | `ALREADY_REDEEMED` |
| Nie udało się ustalić kraju z IP (geolokalizacja niedostępna) | `503` | `COUNTRY_UNRESOLVED` |
| Błąd walidacji / niepoprawny JSON | `400` | `VALIDATION_ERROR` |

Kolejność sprawdzeń przy użyciu kuponu: **istnienie → kraj → unikalność per użytkownik → atomowa
konsumpcja limitu**. Dzięki temu odrzucone żądanie nie pozostawia żadnego efektu ubocznego.

---

## Architektura

Warstwowy podział odpowiedzialności:

```
com.coupons
├── web/                # warstwa HTTP: kontroler, DTO, obsługa błędów, ustalanie kraju z żądania
│   ├── CouponController
│   ├── ClientCountryResolver
│   ├── GlobalExceptionHandler
│   └── dto/            # rekordy: żądania i odpowiedzi
├── service/            # logika biznesowa (transakcje, reguły)
│   ├── CouponService
│   ├── geo/            # abstrakcja geolokalizacji IP + implementacja ip-api.com
│   └── exception/      # wyjątki domenowe z przypisanym kodem i statusem HTTP
├── repository/         # Spring Data JPA + atomowe zapytanie konsumujące limit
├── domain/             # encje JPA: Coupon, CouponRedemption
└── config/             # konfiguracja RestClient (timeouty do usługi geo)
```

Schemat bazy tworzą migracje Flyway (`src/main/resources/db/migration`); Hibernate działa w trybie
`ddl-auto=validate`, więc encje muszą być zgodne ze schematem (schema jest źródłem prawdy).

---

## Kluczowe decyzje projektowe

**Współbieżność — „kto pierwszy, ten lepszy".** Limit użyć egzekwuje pojedynczy, warunkowy `UPDATE`:

```sql
UPDATE coupon SET current_uses = current_uses + 1
 WHERE id = :id AND current_uses < max_uses
```

Baza serializuje aktualizacje wiersza, a warunek `current_uses < max_uses` gwarantuje, że limit
nigdy nie zostanie przekroczony — bez blokad aplikacyjnych i bez wyścigu typu read-then-write.
Poprawność potwierdza test `CouponConcurrencyIntegrationTest` (300 równoległych żądań na kupon z
limitem 50 → dokładnie 50 sukcesów, 250 odmów, `current_uses = 50`).

**Unikalność per użytkownik.** Egzekwowana **ograniczeniem UNIQUE `(coupon_id, user_id)`** w bazie, a
nie sprawdzeniem w kodzie — dzięki temu jest odporna na współbieżne żądania. Kolizja ograniczenia jest
przechwytywana i tłumaczona na `409 ALREADY_REDEEMED`.

**Case-insensitivity kodu.** Kod jest normalizowany do wielkich liter przy zapisie i przy wyszukiwaniu,
a unikalność zapewnia indeks UNIQUE. `WIOSNA` i `wiosna` to ten sam kupon.

**Geolokalizacja IP.** Ukryta za interfejsem `GeoLocationService` (łatwa podmiana/mockowanie).
Implementacja `ip-api.com` ma krótkie timeouty i nigdy nie wywraca żądania przy awarii usługi
zewnętrznej. Dla adresów prywatnych/loopback (typowych w kontenerach) używany jest konfigurowalny kraj
domyślny.

**Spójny kontrakt błędów.** Wyjątki domenowe dziedziczą po `CouponException` i niosą `CouponErrorCode`
(kod + status HTTP), co `GlobalExceptionHandler` tłumaczy na jednolitą kopertę — bez rozgałęzień per
przypadek.

**Skalowalność.** Serwis jest bezstanowy (stan wyłącznie w bazie), więc skaluje się horyzontalnie;
spójność limitu i unikalności gwarantuje warstwa bazodanowa, a nie pamięć instancji.

---

## Konfiguracja

Ustawienia (plik `application.yml`, nadpisywalne zmiennymi środowiskowymi):

| Zmienna | Domyślnie | Opis |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/coupons` | URL bazy |
| `SPRING_DATASOURCE_USERNAME` | `coupons` | użytkownik bazy |
| `SPRING_DATASOURCE_PASSWORD` | `coupons` | hasło bazy |
| `SERVER_PORT` | `8080` | port aplikacji |
| `COUPONS_GEO_BASE_URL` | `http://ip-api.com` | endpoint usługi geolokalizacji |
| `COUPONS_GEO_DEFAULT_COUNTRY` | *(puste)* | kraj dla adresów nie-publicznych (w compose: `PL`) |
| `COUPONS_GEO_ALLOW_COUNTRY_HEADER` | `true` | zezwól na nadpisanie kraju nagłówkiem `X-Country-Code` |

> **`X-Country-Code`** to udogodnienie deweloperskie: pozwala testować regułę kraju bez publicznego,
> routowalnego IP i bez zależności od usługi zewnętrznej. W środowisku produkcyjnym z prawdziwymi
> adresami klientów można je wyłączyć ustawiając `COUPONS_GEO_ALLOW_COUNTRY_HEADER=false`.

---

## Budowanie i uruchamianie lokalnie

Wymagania: JDK 21 (Maven nie jest konieczny — dostępny jest wrapper `./mvnw`).

```bash
# Zbudowanie pakietu (bez testów)
./mvnw clean package -DskipTests

# Uruchomienie (wymaga działającej bazy PostgreSQL, np. z docker compose up db)
./mvnw spring-boot:run
```

Zbudowany artefakt: `target/coupon-service-1.0.0.jar` → `java -jar target/coupon-service-1.0.0.jar`.

---

## Testy

Testy integracyjne uruchamiają aplikację na **prawdziwej bazie PostgreSQL** w kontenerze
(Testcontainers) — te same migracje Flyway i ta sama semantyka współbieżności co na produkcji.

```bash
./mvnw test
```

Zakres testów (`10` testów):

- `CouponApiIntegrationTest` — tworzenie, duplikat (case-insensitive), użycie, 404, wyczerpanie,
  zły kraj, powtórne użycie przez tego samego usera, walidacja, niepoprawny JSON.
- `CouponConcurrencyIntegrationTest` — gwarancja „kto pierwszy ten lepszy" pod obciążeniem.

> **Uwaga (Docker 29+):** bardzo nowe wersje demona Docker wymagają API ≥ 1.40, podczas gdy klient
> docker-java w Testcontainers domyślnie próbuje wersji 1.32. Jeśli testy zgłaszają *„client version
> 1.32 is too old"*, uruchom je wskazując wersję API:
> ```bash
> DOCKER_API_VERSION=1.44 ./mvnw test -DargLine="-Dapi.version=1.44"
> ```
> Na standardowych środowiskach CI (Docker ≤ 28) workaround nie jest potrzebny.

---

## Wymagania

Mapowanie wymagań funkcjonalnych na realizację:

| Wymaganie | Realizacja |
| --- | --- |
| Tworzenie kuponu (bez uwierzytelniania) | `POST /api/v1/coupons` |
| Rejestracja użycia kuponu | `POST /api/v1/coupons/{code}/redemptions` |
| Pola kuponu: kod, data utworzenia, max użyć, bieżące użycia, kraj | encja `Coupon` + tabela `coupon` |
| Unikalny kod, wielkość liter bez znaczenia | normalizacja do wielkich liter + UNIQUE |
| Limit użyć — „kto pierwszy ten lepszy" | atomowy warunkowy `UPDATE` |
| Ograniczenie krajem na podstawie IP | `GeoLocationService` (ip-api.com) + `ClientCountryResolver` |
| Stosowne komunikaty: brak kuponu / wyczerpany / zły kraj / już użyty | jednolite kody błędów |
| (Opcjonalnie) jeden użytkownik = jedno użycie | `userId` + UNIQUE `(coupon_id, user_id)` |
| Skalowalność, baza danych, Java, Maven/Gradle | bezstanowy serwis, PostgreSQL, Java 21, Maven |
| Środowisko wielowątkowe | atomowość na poziomie bazy + test współbieżności |

---

## Licencja

Projekt udostępniony na licencji [MIT](LICENSE).
