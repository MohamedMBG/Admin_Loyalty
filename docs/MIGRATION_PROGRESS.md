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
| 7 | Clients tab: client roster | `GET /admin/users?limit=` | *this change* |
| 8 | Client details: header profile | `GET /admin/users/{uid}` | *this change* |
| 10 | Rewards admin: catalog create/update/delete | `POST/PUT/DELETE /admin/rewards` | *this change* |
| 11 | Create-cashier provisioning | `POST /admin/cashiers` | *this change* |

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

### Unit 7 detail — clients tab roster

Backend PR #45 added `GET /admin/users`, so the Clients tab no longer needs to read the whole
`users` collection directly. Files:

- `data/ClientsSummaryRepository.java` — `getClients()` + `getClientEarnActivities()` (a per-row
  N+1 activity query) removed; added blocking `listUsers(limit)` → `GET /admin/users?limit=`.
- `viewmodel/ClientsSummaryViewModel.java` — parses `{users:[…]}` on the IO executor into `Client`
  objects. The old two-pass load (list users, then one activities query per user to sum points)
  collapses to a single call — the roster already carries `points`/`visits`.

**Behaviour changes (intentional):**

1. **Average spend zeroed** — the backend roster carries no spend metric (the old value was
   `sumPoints / visits`, not real currency). The tile shows `0.00` until a real analytics field
   exists.
2. **Ordering** — backend returns an unordered capped page (was `orderBy(points desc).limit(100)`).
   The client can sort locally if needed.

### Unit 8 detail — client details header

Backend PR #45 added `GET /admin/users/{uid}`, replacing the direct `users/{uid}` read that fed
the client-details header (kept as a flagged direct read in unit 6). Files:

- `data/ClientDetailsRepository.java` — `getUserProfile(uid)` now calls `GET /admin/users/{uid}`
  (blocking) instead of Firestore; the `ponytail:` gap comment is removed.
- `viewmodel/ClientDetailsViewModel.java` — `loadUserProfile` runs on the IO executor and parses
  the detail JSON into a small `AdminUserDetail` holder instead of a Firestore `DocumentSnapshot`.
- `fragments/ClientDetailsFragment.java` — header observer reads the holder's typed fields;
  `lastVisit` now shows `lastEarnAt` (closest backend proxy).

### Unit 10 detail — rewards catalog CRUD

Backend PR added `POST/PUT/DELETE /admin/rewards`. Only the **writes** move — reading
`rewards_catalog` stays a direct Firestore read (rules allow any signed-in user), so the live
list keeps its snapshot listener. Files:

- `data/api/AdminApiClient.java` — added `put()` + `delete()` (were POST/GET only).
- `data/RewardsAdminRepository.java` — `getRewardsQuery()` (live read) kept; `addReward`/
  `updateReward`/`deleteReward` now hit the backend. Maps the admin `RewardItem`
  (`costPoints`/`isVisible`) onto the backend catalog schema (`cost`/`active`).
- `viewmodel/RewardsAdminViewModel.java` — writes run on the IO executor; errors mapped via
  `ApiErrors` (+ `INVALID_REWARD`/`REWARD_NOT_FOUND`).

### Unit 11 detail — cashier provisioning

Backend PR added `POST /admin/cashiers`. The old client flow was both insecure and rules-broken:
it spun up a **secondary FirebaseApp** to create the auth user and wrote `users/{uid}` with
`role: cashier` directly (a client can neither set custom claims nor write another user's doc).
The backend now does all three (create auth user → set `role: cashier` claim → write the doc),
with rollback of the orphaned auth user if the claim step fails. Files:

- `data/CreateCashierRepository.java` — the secondary-FirebaseApp + Firestore write replaced by a
  single `POST /admin/cashiers {email,password,name}` call. Password is sent once over HTTPS, never
  stored.
- `viewmodel/CreateCashierViewModel.java` — the two-step create collapses to one IO-thread call;
  errors mapped (`INVALID_CASHIER`/`CASHIER_EMAIL_EXISTS`). Public method signature unchanged, so
  the fragment is untouched.

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

- ~~**list-users**~~ and ~~**get-user-by-uid**~~ — **RESOLVED** by backend PR #45
  (`GET /admin/users`, `GET /admin/users/{uid}`); migrated in units 7 + 8.
- **Dashboard / Logs / RewardLogs** — read `earn_codes` / `redeem_codes` / `users` aggregates
  directly. No analytics endpoints exist. Still blocked.

---

## Remaining

| Unit | What | Endpoint | Status |
|------|------|----------|--------|
| 9A | Earn codes priced in MAD (revenue data capture) | `POST /admin/earn-codes {amountMad}` | done — backend #47 / admin #13 |
| 9B | Analytics endpoint (revenue / points / gifts / new clients / per-cashier) | — (to build) | next |
| 9C | Migrate Dashboard / Logs / RewardLogs to the analytics endpoint | — | after 9B |
| — | **Hard cutover:** deploy `firestore.rules` once both apps' economy paths route through the backend | — | pending |

Units 1–8, 10, 11 done. Only the **analytics** surfaces (unit 9) remain, and they are not just an
"add an endpoint" job — they read fields the backend no longer writes:

**Revenue — DECIDED (2026-07-09):** method (a) — the cashier enters a **MAD amount**, the backend
converts at **50 points per 1 MAD** and stores `earn_codes.amountMAD`; revenue = `sum(amountMAD)`.

**Stage 9A — DONE** (backend #47 / admin #13): earn codes now capture the money amount. Pricing
moved off the client (was a placeholder `/5` ratio) to backend-owned `POINTS_PER_MAD`.

Remaining stages:

- **9B — analytics endpoint.** `GET /admin/analytics?period=…` → revenue (`sum(amountMAD)`), points
  issued/redeemed, gifts, new clients, time-bucketed chart, and (see below) per-cashier stats.
- **9C — migrate the screens** `DashboardRepository` / `LogsRepository` / `RewardLogsRepository`
  onto 9B. Logs/RewardLogs become aggregated summaries; no raw global code dump is reintroduced.

**Per-cashier stats — open question for 9B.** Earn codes already store `createdBy`, so per-cashier
**earn** attribution needs no new field. Attributing **redeem completions** to a cashier still needs
a field on `redeem_codes` — decide whether that column is wanted before building the panel.

---

## Unit 12 — Segmented push campaigns (2026-07-18)

Status: implemented; compilation and feature-specific tests verified.

- `InboxRepository` now uses the authenticated `AdminApiClient` and backend
  `/admin/push/preview` + `/admin/push/send` routes. The prior unrelated Vercel email/push service
  has been removed from this flow.
- Every send has a client idempotency key; authorization is enforced again by the backend's admin
  role check.
- Inbox filters now include gender, inclusive age, birthday today, neighborhood, top interest
  (coffee, tea, pastries, breakfast, lunch), recent visit (3/7/30 days), and lapsed visit
  (30/90 days).
- The preview displays unique reachable customers. The send result reports reachable customers,
  successful device deliveries, and failures.
- Verification: `compileDebugJavaWithJavac --no-daemon --max-workers=1` and
  `InboxRepositoryTest` passed. The full suite still has three unrelated pre-existing
  `DashboardViewModelTest` failures caused by Mockito inline initialization on Java 21. A staging
  FCM send is still required after backend deployment.
