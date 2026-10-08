# Allegro Lister

Aplikacja webowa w Javie (Spring Boot 3, Java 21) do masowego wystawiania i zarządzania ofertami Allegro.
Funkcje są wzorowane na module Allegro w BaseLinkerze: magazyn produktów, formularz wystawiania z operacjami
grupowymi, produktyzacja po EAN, szablony opisów z tagami, powiązania kategorii, reguły parametrów,
zarządzanie ofertami i synchronizacja stanów.

## Rozeznanie: BaseLinker → Allegro Lister → API Allegro

| Funkcja w BaseLinkerze | W tej aplikacji | Wywołania API Allegro |
|---|---|---|
| Podłączanie wielu kont Allegro | Konta → Połącz konto (produkcja / sandbox) | OAuth 2.0 Authorization Code + PKCE, refresh token, `GET /me` |
| Magazyn produktów | Magazyn: ręcznie, import CSV, API `PUT /api/products/{sku}/stock` | — |
| Wystaw zaznaczone (formularz) | Magazyn → zaznacz → Wystaw zaznaczone | `POST /sale/product-offers` |
| Produktyzacja (katalog po EAN / nowy produkt) | Tryb: automatycznie / tylko katalog / nowy produkt | `GET /sale/products?mode=GTIN`, `productSet[].product.id` albo pełne dane produktu |
| Weryfikacja oferty przez Allegro | Status „Weryfikacja Allegro", odpytywanie co 15 s | 202 + `Location` → `GET /sale/product-offers/{id}/operations/{opId}` (202 / 303) |
| Szablony aukcji (sekcje + tagi) | Szablony: sekcje tekst/zdjęcie, tagi `[nazwa]`, `[opis]`, `[cechy]`, `[cecha:X]`, `[zdjecie_N]`… | `description.sections[].items[]` (TEXT / IMAGE), tylko h1, h2, p, ul, ol, li, b |
| Zdjęcia | Wgrywanie na serwery Allegro przed wystawieniem | `POST upload.allegro.pl/sale/images` |
| Kategorie i dopasowanie kategorii (AI w BL) | Drzewo kategorii, „Dopasuj" (propozycje Allegro), powiązania sklep → Allegro | `GET /sale/categories`, `GET /sale/matching-categories` |
| Reguły parametrów | Z cechy, stała, stała gdy pole zawiera frazę (`czerw*`), mapowanie wartości | `GET /sale/categories/{id}/parameters` |
| Ustawienia ofert konta | Domyślna kategoria, szablon, cennik, zwroty, reklamacje, gwarancja, mnożnik i dodatek do ceny, czas wysyłki, faktura | `GET /sale/shipping-rates`, `/after-sales-service-conditions/*` |
| GPSR | Producent i osoba odpowiedzialna, informacje o bezpieczeństwie | `GET /sale/responsible-producers`, `/sale/responsible-persons`, `productSet[].safetyInformation` |
| Zarządzanie ofertami | Import ofert, powiązanie po sygnaturze (SKU), operacje grupowe | `GET /sale/offers`, `PATCH /sale/product-offers/{id}` |
| Zmień cenę / ilość / cennik / zakończ / wznów | Operacje grupowe | `PUT /sale/offer-price-change-commands`, `…/offer-quantity-change-commands`, `…/offer-modification-commands`, `…/offer-publication-commands` |
| Zmień opis / tytuł | Aktualizacja opisu i zdjęć z szablonu, tytuł z magazynu | `PATCH /sale/product-offers/{id}` |
| Wystaw na innym koncie | Oferty → zaznacz → Utwórz formularz wystawiania na innym koncie | jak wystawianie |
| Moduł synchronizacji stanów | Co 15 min: ilość, cena, kończenie przy stanie 0, wznawianie | komendy grupowe + `PATCH` |

Poza zakresem pierwszej wersji: oferty wielowariantowe, licytacje, zamówienia, promowanie, tłumaczenia na
rynki zagraniczne, AI do opisów. Struktura kodu jest przygotowana pod ich dodanie.

## Uruchomienie

Wymagania: Java 21 i Maven 3.9 (albo IntelliJ IDEA, które ma wbudowanego Mavena).

```bash
# 1. dane aplikacji Allegro (patrz niżej)
export ALLEGRO_SANDBOX_CLIENT_ID=...
export ALLEGRO_SANDBOX_CLIENT_SECRET=...
export APP_ENCRYPTION_KEY="dowolny-długi-losowy-tekst"

# 2. start
mvn spring-boot:run
# albo: mvn package && java -jar target/allegro-lister-1.0.0.jar
```

Panel: <http://localhost:8080>. Dane trzymane są w pliku H2 `./data/allegrolister.mv.db`.
PostgreSQL: uruchom z `--spring.profiles.active=postgres` i ustaw `DB_URL`, `DB_USER`, `DB_PASSWORD`.

Docker:

```bash
docker build -t allegro-lister .
docker run -p 8080:8080 -v $(pwd)/data:/app/data \
  -e ALLEGRO_SANDBOX_CLIENT_ID=... -e ALLEGRO_SANDBOX_CLIENT_SECRET=... \
  -e APP_ENCRYPTION_KEY=... -e APP_PASSWORD=haslo allegro-lister
```

### Rejestracja aplikacji w Allegro

1. Wejdź na <https://apps.developer.allegro.pl> (produkcja) albo
   <https://apps.developer.allegro.pl.allegrosandbox.pl> (sandbox, testowe konto).
2. Dodaj aplikację typu „aplikacja będzie posiadać dostęp do przeglądarki".
3. Adres przekierowania: `http://localhost:8080/allegro/callback` (albo `{APP_BASE_URL}/allegro/callback`).
4. Client ID i Client Secret wpisz do zmiennych środowiskowych lub `application.yml`.
5. W panelu: Konta → Połącz konto → zaloguj się na Allegro i zatwierdź dostęp.

Na koncie testowym ustaw na Allegro cennik dostawy oraz warunki zwrotów i reklamacji - bez nich Allegro
odrzuci ofertę.

### Konfiguracja (zmienne środowiskowe)

| Zmienna | Znaczenie |
|---|---|
| `ALLEGRO_CLIENT_ID`, `ALLEGRO_CLIENT_SECRET` | aplikacja produkcyjna |
| `ALLEGRO_SANDBOX_CLIENT_ID`, `ALLEGRO_SANDBOX_CLIENT_SECRET` | aplikacja w sandboxie |
| `APP_BASE_URL` | publiczny adres panelu (redirect URI), domyślnie `http://localhost:8080` |
| `APP_ENCRYPTION_KEY` | klucz szyfrowania tokenów w bazie (AES-GCM) |
| `APP_USER`, `APP_PASSWORD` | logowanie HTTP Basic do panelu; puste hasło = bez logowania |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | baza danych |

Jeśli panel ma być dostępny z internetu: ustaw `APP_PASSWORD` i wystaw go za HTTPS (np. reverse proxy).

## Jak pracować (jak w BaseLinkerze)

1. **Konta** → połącz konto i w **Ustawieniach** wybierz domyślny cennik, zwroty, reklamacje, szablon,
   mnożnik ceny i dane GPSR.
2. **Magazyn** → dodaj produkty albo zaimportuj CSV (przykład: *Pobierz przykładowy plik*).
3. **Szablony** → zbuduj opis z sekcji. Podgląd pokazuje opis na danych wybranego produktu.
4. **Kategorie i parametry** → powiąż kategorie ze sklepu z kategoriami Allegro i dodaj reguły parametrów.
5. **Magazyn** → zaznacz produkty → **Wystaw zaznaczone**. W formularzu:
   - operacje grupowe (kategoria, szablon, cennik, ceny, tytuły…),
   - *Dopasuj kategorie przez Allegro*, *Sprawdź przed wystawieniem* (brakujące parametry, tytuły, zdjęcia),
   - *Parametry* - ręczne wartości dla pojedynczego produktu,
   - *Wystaw na Allegro* - statusy odświeżają się same; błędy Allegro widać przy produkcie.
6. **Oferty** → *Importuj / odśwież z Allegro*, operacje grupowe, wystawianie na innym koncie.
7. Synchronizacja stanów: włącz w ustawieniach konta. Stan w magazynie aktualizujesz ręcznie, importem
   CSV albo z innego systemu:

```bash
curl -X PUT http://localhost:8080/api/products/KUB-001/stock \
  -H 'Content-Type: application/json' -d '{"quantity": 12, "price": "24.90"}'
```

Uwaga: aplikacja nie pobiera zamówień, więc sprzedaż na Allegro nie zmniejsza stanu w jej magazynie.
Dlatego wznawiane są tylko oferty, które zakończyła sama synchronizacja z powodu stanu 0.

### Format CSV

Nagłówek (kolejność dowolna, `;` `,` albo tab):
`sku;ean;nazwa;producent;cena;ilosc;vat;waga;kategoria;zdjecia;opis;cechy;kategoria_allegro;bezpieczenstwo`

- `zdjecia`: adresy rozdzielone `|` (pierwsze = główne)
- `cechy`: `Kolor=czerwony|Materiał=bawełna` - źródło parametrów Allegro
- `kategoria_allegro`: ID kategorii (opcjonalne, nadpisuje powiązanie)

### Jak uzupełniane są parametry

Dla każdego parametru kategorii, po kolei:
1. wartość wpisana ręcznie w edytorze parametrów,
2. reguły (najpierw dla tej kategorii, potem globalne, wg priorytetu),
3. automatycznie: EAN, cecha o tej samej nazwie (bez względu na wielkość liter i polskie znaki),
   marka = producent, „Stan" = Nowy, waga produktu.

Wartości słownikowe są dopasowywane do słownika Allegro; liczby normalizowane (`12,5 cm` → `12.5`);
zakresy wpisuj jako `3-6`. Gdy Allegro zgłosi brak parametrów produktu z katalogu, aplikacja automatycznie
ponawia wystawienie z własnymi parametrami.

## Struktura kodu

```
pl.allegrolister
├── allegro/      klient REST API Allegro: OAuth, kategorie, katalog, ustawienia sprzedawcy, zdjęcia, oferty, komendy
├── domain/       encje JPA: konto, produkt, szablon, powiązania, reguły, formularz wystawiania, oferta, log
├── repo/         repozytoria Spring Data
├── service/      logika: tagi, sanitizer HTML, parametry, payload oferty, wystawianie, oferty, synchronizacja
├── web/          kontrolery MVC + API JSON
└── config/       konfiguracja, logowanie Basic + ochrona CSRF, szyfrowanie tokenów
```

Najważniejsze klasy: `ListingPublisher` (cały proces wystawienia jednej oferty), `ParameterResolver`,
`DescriptionRenderer` + `AllegroHtmlSanitizer`, `OfferPayloadBuilder`, `OfferService`, `StockSyncService`.

## Testy

```bash
mvn test
```

Testy jednostkowe sprawdzają sanitizer HTML, tagi szablonów, dopasowanie parametrów, budowę payloadu oferty
i parser CSV. `PageRenderingTest` uruchamia aplikację na H2 w pamięci i renderuje wszystkie strony panelu
(Allegro jest zamockowane).

Przed pracą na koncie produkcyjnym przetestuj całość na sandboxie Allegro.
