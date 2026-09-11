# ai-order — Kopi Harmoni AI Ordering (Vaadin Frontend)

Frontend aplikasi "Kopi Harmoni": chat AI untuk memesan menu. Aplikasi Vaadin + Spring Boot yang berjalan sebagai UI terpisah dan berkomunikasi dengan service `customer` (backend) melalui HTTP API. Semua data customer, session, dan menu tersimpan di backend — UI tidak menyentuh database sama sekali.

## Tech Stack

| | |
|---|---|
| Bahasa | Java 21 |
| Framework | Spring Boot 4.1.1 |
| UI | Vaadin 25.2.6 (Community Edition — gratis) |
| Reaktif | Spring WebFlux + Reactor (`WebClient`, SSE untuk streaming AI) |
| JSON | Jackson 3.x (`tools.jackson.*`) |
| Boilerplate | Lombok |
| Validasi | Bean Validation (Jakarta) |

> Catatan: Vaadin 24.x tidak kompatibel dengan Spring Boot 4.x. Versi saat ini
> (25.2.6, free CE) dipilih karena terbukti jalan dengan Spring Boot 4.1.1.
> Jackson 3.x memakai namespace `tools.jackson.core` / `tools.jackson.databind`
> (bukan `com.fasterxml.*`).

## Arsitektur

Sepenuhnya decoupled dari backend — alur panggilan hanya satu arah:

```text
ui (Vaadin views + komponen chat)
        ↓
service (OrderingService, CustomerService)
        ↓
client (WebClientConfig, *Client → HTTP API)
        ↓
customer backend (http://localhost:8084)
```

- `ui/` — View: `LandingView` (`/`), `MainLayout` (shell), `OrderView` (`/order?sessionId=...`). Komponen chat: `AiMessage`, `UserMessage`, `ChatInput`, `TypingIndicator`, `Header`, `CustomerGateDialog`.
- `service/` — `OrderingService` (stream + blocking chat), implementasinya `CustomerAiOrderingService` (via HTTP), `CustomerService` (resolve customer), `CustomerClientService`.
- `client/` — `WebClientConfig` mem-build bean `customerWebClient`; `CustomerApiProperties` terikat ke properti `customer.api.*` menggantikan `@Value`. HTTP clients: `AiChatClient`, `SessionClient`, `CustomerClient`, `CategoryClient`, `ProductClient`.
- `dto/` — record DTO hasil mapping respons JSON backend.

## Alur Chat

1. `OrderView` membaca `sessionId` query param (UUID).
2. Belum ada customer terhubung → `CustomerGateDialog` tampil: login via nomor telepon (login jika ada, register jika belum). Hasilnya disimpan di `VaadinSession` (`customerId`).
3. Pesan user → `OrderingService.streamMessage`. Bila `customerSessionId` belum ada, dibuat via `POST /api/v1/customer-sessions` lalu dicache di `VaadinSession`.
4. Respons AI di-stream via SSE (`chat/stream`) dan dirender sebagai bubble per token dengan `TypingIndicator` saat menunggu.
5. Fallback: bila stream gagal atau tidak menghasilkan token, `OrderingService.sendMessage` (blocking, satu respons) dipanggil.

## Konfigurasi (`application.yaml`)

| Key | Default | Keterangan |
|---|---|---|
| `server.port` | `8085` | port aplikasi ini |
| `app.store-name` | `Kopi Harmoni` | nama toko di header |
| `customer.api.base-url` | `http://localhost:8084` | base URL backend customer |
| `customer.api.timeout-ms` | `60000` | timeout connect/response `customerWebClient` |
| `customer.api.source` | `AI_CHAT` | label `source` pada customer session |

Timeout sengaja besar (60 detik) karena first-token AI bisa memakan waktu lebih dari 5 detik.

## Menjalankan

Prasyarat: backend `customer` berjalan di `http://localhost:8084`.

```bash
mvn spring-boot:run
```

Buka `http://localhost:8085`.

## Navigasi

| Route | Keterangan |
|---|---|
| `/` | landing; resolve sessionId (URL param > VaadinSession > UUID baru), tampilkan gate atau redirect ke order |
| `/order?sessionId=<uuid>` | chat order |

## Endpoint API Backend yang Dipakai

| Method | Path |
|---|---|
| POST | `/api/v1/customers` |
| GET | `/api/v1/customers?phone=` |
| GET | `/api/v1/customers/{id}` |
| POST | `/api/v1/customer-sessions` |
| POST | `/api/v1/customer-sessions/{id}/chat` |
| POST | `/api/v1/customer-sessions/{id}/chat/stream` (SSE) |
| GET | `/api/v1/customer-sessions/{id}/categories` |
| GET | `/api/v1/customer-sessions/{id}/products/category/{categoryId}` |

## Build & JavaDoc

```bash
mvn compile
mvn javadoc:javadoc   # bersih, 0 warning
```

## Testing

Dependensi test sudah disiapkan (`spring-boot-starter-test`, `reactor-test`, karibu-testing v24 + v10-spring) namun belum ada test case — folder `src/test` masih kosong.

## CSS Theme

Seluruh styling terkumpul di `src/main/resources/META-INF/resources/frontend/themes/app/styles.css`: design system (warna, shadow, radius, spacing), dukungan dark mode via `prefers-color-scheme`, dan layout responsif.

## Catatan Development

- Error `npm install` pada dev server Vaadin (`vaadin-dev`, optional) umumnya masalah jaringan — jalankan `npm install` di direktori proyek untuk memperbaikinya. Aplikasi tetap berjalan tanpa dev server.
- Aplikasi ini tidak punya autentikasi sendiri; identitas customer diselesaikan via nomor telepon pada `CustomerGateDialog`.