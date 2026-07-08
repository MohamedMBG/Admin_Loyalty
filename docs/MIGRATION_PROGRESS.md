# Admin App — Backend Migration Progress

Tracks the migration of the BeanLoyal **admin** app off direct Firestore economy
writes/reads and onto the Spring Boot backend (`/api/v1`). Source plan:
[`ANDROID_IMPLEMENTATION_PLAN - Copy.md`](../ANDROID_IMPLEMENTATION_PLAN%20-%20Copy.md).

**Why:** once `firestore.rules` deploys, the client SDK can only read its *own* profile +
activity and the public catalogs. Every admin read/write of *other* users, codes, or audit
records is denied and must route through the backend (Admin SDK bypasses rules).

---

## Done

| # | Unit | Endpoint | Commit / PR |
|---|------|----------|-------------|
| 1 | REST foundation — `AdminApiClient` (OkHttp), `AuthInterceptor` (bearer + 401 force-refresh retry), `ApiResult` envelope, idempotency key helper | — | PR #4 (`9089161`, `a2a2b46`) |
| 2 | Cashier: create earn code | `POST /admin/earn-codes` | PR #6 (`f35815d`) |
| 3 | Cashier: complete redeem | `POST /cashier/redeem/complete` | PR #7 (`789344b`) |
| 4 | Points adjustment | `POST /admin/users/{uid}/points-adjustment` | PR #8 (`ca70f22`) |
| 5 | User search (cashier redeem screen) | `GET /admin/users/search?email=\|phone=` | *this change* |
| 6 | Client details: activity history | `GET /admin/users/{uid}/activity` | *this change* |

### Unit 5 detail — user search

Replaced 4 client Firestore queries (`searchUserBy{Email,Phone,Uid,Name}`) with a single
backend call. Files:

- `models/AdminUser.java` — **new.** Typed mirror of backend `UserSearchResponse.UserSummary`
  (`uid, email, phone, points, visits`). No `fullName`/gender/birthday/address — backend does
  not expose them.
- `data/RedeemingRepository.java` — 4 search methods + `getActivePromotions()` removed; added
  blocking `searchUser(email, phone)` → `GET /admin/users/search`. URL-encodes the param.
- `viewmodel/RedeemingViewModel.java` — `searchUser` runs on the IO executor and parses the
  `{users:[…]}` array; `selectedUser` is now `LiveData<AdminUser>`. Deleted the dead
  promo-eligibility path (`verifySystemPromotions`, `runVerificationLogic`, `calculateAge`,
  promo LiveData).
- `cashier/RedeemingActivity.java` — consumes `AdminUser` instead of `DocumentSnapshot`;
  removed the promo observer + orphaned `setEligibilitySuccess`.

**Behaviour changes (intentional):**

1. **No full name** — search returns none. UI shows **email** as the client label.
2. **Search narrowed to exact email OR phone** — uid + name lookup dropped (backend
   unsupported, and direct `users` query is rules-denied anyway).
3. **Promo eligibility removed** from the cashier redeem screen — it read the `promotions`
   collection (absent from `firestore.rules` → denied at cutover) and needed profile fields the
   backend never exposes. Dead post-deploy.

### Unit 6 detail — client details activity history

Replaced the direct `earn_codes` + `redeem_codes` reads (both rules-denied) that built the
history list with `GET /admin/users/{uid}/activity`. Files:

- `data/ClientDetailsRepository.java` — added blocking `getUserActivity(uid, limit)`; removed
  `getUserEarnCodes`, `getUserRedeemCodes`, `getCashiers`. `getUserProfile(uid)` **kept** (see gap
  below).
- `viewmodel/ClientDetailsViewModel.java` — `loadHistory` runs on the IO executor and parses
  `{activities:[…]}` → `ActivityItem`; removed the MAD avg-spend calc and `fetchCashiers`.
- `fragments/ClientDetailsFragment.java` — removed the cashier-filter chip + observer +
  `filterListByCashier`.

**Behaviour changes (intentional):**

1. **Activity types collapsed** to credit/debit by `pointsDelta` sign — the feed's fine-grained
   `earn|redeem|cancel|expire|birthday|adjust` types are flattened to the UI's green-`+` /
   red-`−` rows (all the UI ever distinguished).
2. **No cashier attribution or reward name** — the feed carries neither, so history rows show
   "System" / "Reward". Cashier filter removed.
3. **Average spend zeroed** — was derived from `earn_codes.amountMAD`, which the backend no
   longer exposes (earn codes are points-direct). No analytics endpoint for it.

### Audit — nothing to migrate

The admin app has **no consumer** that reads the `audit` collection. `GET /admin/audit` has no
home yet; no code change needed. (Listed in the plan only because a hypothetical direct read
would be rules-denied.)

---

## Open flags (need owner decision)

- **Authz mismatch on search.** `/admin/users/search` requires the **admin** role, but this is
  the cashier redeem flow. A pure cashier gets **403**. Either add a cashier-allowed search
  endpoint, or accept that these screens are admin-only. (UX handles it: 403 → "Not authorized.
  Admin role required.")
- **Promo eligibility** in the cashier screen is gone — restore needs a backend endpoint
  (`promotions` read + user profile fields).
- Backend gaps from the plan still open: catalog CRUD endpoint, cashier-provisioning endpoint
  (set `role` claim), device unregister on logout.

### New backend gaps found during unit 6 (block whole screens at cutover)

The backend exposes users only via **search (email/phone)** and **activity (by uid)**. It has
**no** "list all users" and **no** "get one user's full profile by uid". That leaves several
admin surfaces with direct `users` reads and no endpoint to migrate to — they will break when
`firestore.rules` deploys:

- **Clients tab** (`ClientsSummaryRepository.getClients`) — reads the entire `users` collection
  + each user's `activities`. Needs a **list-users** endpoint (ideally paginated, with points).
- **Client details header** (`ClientDetailsRepository.getUserProfile`) — reads `users/{uid}` for
  name / gender / address / lastVisit. Needs a **get-user-by-uid** endpoint (with those fields).
  Kept as a direct read for now (flagged with a `ponytail:` comment) so the header renders
  pre-cutover.
- **Dashboard / Logs / RewardLogs** — read `earn_codes` / `redeem_codes` / `users` aggregates
  directly. No analytics endpoints exist.

---

## Remaining

| Unit | What | Endpoint | Status |
|------|------|----------|--------|
| 7 | Clients tab: list all users | — (**backend gap:** list-users) | blocked |
| 8 | Client details header profile | — (**backend gap:** get-user-by-uid) | blocked |
| 9 | Dashboard / Logs / RewardLogs analytics | — (**backend gap:** analytics) | blocked |
| 10 | Rewards admin catalog CRUD | — (**backend gap**) | blocked |
| 11 | Create-cashier provisioning | — (**backend gap:** set `role` claim) | blocked |
| — | **Hard cutover:** deploy `firestore.rules` once both apps' economy paths route through the backend | — | pending |

Everything migratable with today's backend is done (units 1–6). The remaining units all need
new backend endpoints before the admin app can route them off direct Firestore.
