# ai-order — Kopi Harmoni AI Ordering

Customer-facing **Vaadin** chat application for ordering at Kopi Harmoni. The
customer signs in with a phone number, then talks to the AI assistant to browse
the menu, choose items and place an order. The UI is a thin frontend: it owns no
database and talks only to the **customer service** (`8084`) over HTTP, which in
turn hosts the AI assistant, the cart and the menu/order tools.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| UI | Vaadin 25.2.6 (Flow, server-side components, React/Vite frontend) |
| Reactive | Spring WebFlux + Reactor (`WebClient`, SSE for streaming AI) |
| JSON | Jackson 3.x (`tools.jackson.*`) |
| Markdown | CommonMark 0.24.0 + GFM tables |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| Testing | JUnit 5, Reactor Test, Karibu Testing (v24 + v10-spring) |

> Vaadin 24.x is not compatible with Spring Boot 4.x, so this app uses Vaadin
> 25.2.6 (free Community Edition). Jackson 3.x uses the `tools.jackson.core` /
> `tools.jackson.databind` namespace, not `com.fasterxml.*`.

## Architecture

The app is fully decoupled from the backend. Calls flow one way:

```
ui (Vaadin views + chat components)
        ↓
service (OrderingService, CustomerService)
        ↓
client (WebClientConfig, *Client → HTTP API)
        ↓
customer backend (http://localhost:8084)
```

```
com.harmoni.pos.aiorder
├── Application            # @SpringBootApplication, @Theme("app"), @Push, @PWA
├── ui
│   ├── LandingView        # / — session resolution + customer gate
│   ├── MainLayout         # empty AppLayout shell
│   ├── OrderView          # /order — the chat
│   ├── component          # AiMessage, UserMessage, ChatInput, TypingIndicator,
│   │                      # Header, CustomerGateCard, MarkdownText, QuickReplyChips
│   └── support            # AssistantMessageSanitizer, MenuChipFactory
├── service
│   ├── OrderingService            # interface: ensureSession, streamMessage, sendMessage
│   ├── CustomerAiOrderingService  # HTTP-backed implementation
│   ├── CustomerService            # interface: exists, getCustomerResponse, findByPhone, loginOrRegister
│   └── CustomerClientService      # HTTP-backed implementation
├── client
│   ├── WebClientConfig            # customerWebClient + cartWebClient beans
│   ├── CustomerApiProperties      # customer.api.*
│   ├── CartApiProperties          # customer.api.cart.*
│   ├── AiChatClient               # /chat and /chat/stream (SSE)
│   ├── SessionClient              # create customer session
│   ├── CustomerClient             # create / search by phone / get by id
│   ├── CategoryClient             # categories for the session
│   ├── ProductClient              # products for a category
│   └── CartClient                 # add item to the customer cart
├── cart/dto               # AddCartItemRequest
└── dto                    # records mapped from backend JSON
```

### Layers

- **UI** — Vaadin views and components. `LandingView` resolves the session and
  shows the sign-in card; `OrderView` renders the conversation and streams the
  assistant's reply. Components are plain Vaadin components with CSS classes
  from the `app` theme.
- **Service** — `OrderingService` (streaming and blocking chat) and
  `CustomerService` (customer resolution). Implementations call the clients and
  keep the resolved ids in the `VaadinSession`.
- **Client** — `WebClient`-based HTTP clients. `WebClientConfig` builds a
  `customerWebClient` (base URL + timeout) and a `cartWebClient`. Endpoint paths
  come from `CustomerApiProperties` / `CartApiProperties`, not hardcoded.
- **DTO** — Java records mapped from the backend JSON.

## Chat flow

1. `LandingView` (`/`) resolves a `sessionId`: URL parameter → `VaadinSession`
   → new UUID. It stores it in the session and asks `CustomerService.exists`.
2. If a customer is already linked, it forwards to `/order?sessionId=...`.
   Otherwise it shows `CustomerGateCard`.
3. `CustomerGateCard` is a two-step sign-in:
   - **Phone** — one field; submitting looks the number up.
   - **Known number** — signs the customer in.
   - **Unknown number** — the card grows into name/email/phone and registers.
   The resolved customer id is stored in the `VaadinSession`.
4. `OrderView` (`/order`) checks the customer again, loads a personalized
   greeting, and attaches real menu categories as quick-reply chips.
5. Sending a message:
   - appends the user bubble and shows `TypingIndicator`;
   - calls `OrderingService.streamMessage`, which lazily creates the backend
     customer session (`POST /api/v1/customer-sessions`) and streams the reply
     from `POST /api/v1/customer-sessions/{id}/chat/stream` (SSE);
   - renders the final assistant bubble, sanitized by
     `AssistantMessageSanitizer`, with quick replies.
6. If the stream fails or yields nothing usable, `OrderingService.sendMessage`
   (blocking `POST /chat`) is used as a fallback.

The `Header` logout button clears the customer, customer session and session id
from the `VaadinSession` and returns to the landing page.

## UI components

| Component | Purpose |
| --- | --- |
| `AiMessage` | Left-aligned assistant bubble with avatar, timestamp and quick replies |
| `UserMessage` | Right-aligned customer bubble with avatar and timestamp |
| `ChatInput` | Multi-line composer; Enter sends, Shift+Enter inserts a newline; disabled while waiting |
| `TypingIndicator` | Animated three-dot "mengetik" bubble while the reply streams |
| `Header` | Store name and logout button |
| `CustomerGateCard` | Two-step phone → register sign-in card |
| `MarkdownText` | Renders markdown (headings, lists, links, code, GFM tables) as sanitized HTML |
| `QuickReplyChips` | Tappable suggestion chips with a label and a payload |

### Support classes

- `AssistantMessageSanitizer` — turns the raw model transcript into customer-safe
  text: strips leaked tool-call JSON, control tokens, `thinking` blocks and
  internal IDs, and replaces ID table columns with sequence numbers.
- `MenuChipFactory` — builds quick-reply chips for categories, products,
  customizations and links. A chip's label is what the customer reads; its
  payload is the message sent to the assistant (often a full order intent).

## Configuration

`src/main/resources/application.yaml`:

```yaml
server:
  port: 8085

app:
  store-name: Kopi Harmoni

customer:
  api:
    base-url: http://localhost:8084
    customers-path: /api/v1/customers
    sessions-path: /api/v1/customer-sessions
    timeout-ms: 60000
    source: AI_CHAT
    cart:
      url: http://localhost:8084
      base-path: /api/v1/customer-sessions
      add-item-path: /cart/items
```

The 60-second timeout is deliberate: the first AI token can take longer than a
few seconds, especially with a local model.

## Running locally

Prerequisite: the **customer service** running at `http://localhost:8084`
(which in turn needs the menu/order services and an AI provider).

```bash
./mvnw spring-boot:run
```

Open `http://localhost:8085`.

## Routes

| Route | Purpose |
| --- | --- |
| `/` | Landing: resolve `sessionId`, show the sign-in card or forward to the order view |
| `/order?sessionId=<uuid>` | Chat with the AI assistant |

## Backend endpoints used

| Method | Path |
| --- | --- |
| `POST` | `/api/v1/customers` |
| `GET` | `/api/v1/customers?search=<phone>&size=10` |
| `GET` | `/api/v1/customers/{id}` |
| `POST` | `/api/v1/customer-sessions` |
| `POST` | `/api/v1/customer-sessions/{id}/chat` |
| `POST` | `/api/v1/customer-sessions/{id}/chat/stream` (SSE) |
| `GET` | `/api/v1/customer-sessions/{id}/categories` |
| `GET` | `/api/v1/customer-sessions/{id}/products/category/{categoryId}` |
| `POST` | `/api/v1/customer-sessions/{id}/cart/items` |

## Testing

```bash
./mvnw test
```

The test suite currently covers `AssistantMessageSanitizer` (leaked tool JSON,
control tokens, ID stripping, table handling). Karibu Testing and Reactor Test
are on the classpath for view and stream tests.

## Theme

All styling lives in
`src/main/resources/META-INF/resources/frontend/themes/app/styles.css`: a
minimal design system (colours, shadows, radii, spacing), dark-mode support via
`prefers-color-scheme`, and responsive layout. The app is also configured as a
PWA (`@PWA` in `Application`).

## Notes

- **No authentication of its own.** Customer identity is resolved by phone in
  `CustomerGateCard`; the app trusts the customer service for identity.
- **The UI owns no data.** Customer, session, menu and cart state live in the
  customer backend; the Vaadin session only caches ids.
- **Streaming with a fallback.** Replies stream over SSE; a failed or empty
  stream falls back to a blocking chat call.
- **Sanitized output.** The model's raw transcript is cleaned before rendering,
  so tool JSON, control tokens and internal IDs never reach the customer.
- **Server push.** `@Push` keeps the streamed tokens and async menu lookups in
  sync with the browser.
