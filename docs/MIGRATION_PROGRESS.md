# Admin App — Backend Migration Progress

Tracks the migration of the BeanLoyal **admin** app off direct Firestore economy
writes/reads and onto the Spring Boot backend (`/api/v1`). Source plan:
[`ANDROID_IMPLEMENTATION_PLAN - Copy.md`](../ANDROID_IMPLEMENTATION_PLAN%20-%20Copy.md).

**Why:** once `firestore.rules` deploys, the client SDK can only read its *own* profile +
activity and the public catalogs. Every admin read/write of *other* users, codes, or audit
records is denied and must route through the backend (Admin SDK bypasses rules).

**Status (2026-07-09):** units 1–8, 10, 11 done. Unit 9 (analytics) is decision-blocked, not
code-blocked. Admin PRs #10 and #11 merged to `master`; admin PR #12 (units 10–11) open and
depends on backend PRs #45 → #46. Merge order: backend #45 → #46 (deploy) → admin #12.

---

## Done

| # | Unit | Endpoint | Commit / PR |
|---|------|----------|-------------|
| 1 | REST foundation — `AdminApiClient` (OkHttp), `AuthInterceptor` (bearer + 401 force-refresh retry), `ApiResult` envelope, idempotency key helper | — | PR #4 (`9089161`, `a2a2b46`) |
| 2 | Cashier: create earn code | `POST /admin/earn-codes` | PR #6 (`f35815d`) |
| 3 | Cashier: complete redeem | `POST /cashier/redeem/complete` | PR #7 (`789344b`) |
| 4 | Points adjustment | `POST /admin/users/{uid}/points-adjustment` | PR #8 (`ca70f22`) |
| 5 | User search (cashier redeem screen) | `GET /admin/users/search?email=\|phone=` | PR #10 (merged) |
| 6 | Client details: activity history | `GET /admin/users/{uid}/activity` | PR #10 (merged) |
| 7 | Clients tab: client roster | `GET /admin/users?limit=` | PR #11 (merged) |
| 8 | Client details: header profile | `GET /admin/users/{uid}` | PR #11 (merged) |
| 10 | Rewards admin: catalog create/update/delete | `POST/PUT/DELETE /admin/rewards` | PR #12 (open) · backend #46 |
| 11 | Create-cashier provisioning | `POST /admin/cashiers` | PR #12 (open) · backend #46 |

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
- ~~catalog CRUD endpoint~~ and ~~cashier-provisioning endpoint~~ — **RESOLVED** by backend PR #46
  (`POST/PUT/DELETE /admin/rewards`, `POST /admin/cashiers`); migrated in units 10 + 11.
- Backend gap still open (customer-app side): device unregister on logout.

### New backend gaps found during unit 6 (block whole screens at cutover)

- ~~**list-users**~~ and ~~**get-user-by-uid**~~ — **RESOLVED** by backend PR #45
  (`GET /admin/users`, `GET /admin/users/{uid}`); migrated in units 7 + 8.
- **Dashboard / Logs / RewardLogs** — read `earn_codes` / `redeem_codes` / `users` aggregates
  directly. No analytics endpoints exist. Still blocked.

---

## Remaining

| Unit | What | Endpoint | Status |
|------|------|----------|--------|
| 9 | Dashboard / Logs / RewardLogs analytics | — (**backend gap:** analytics) | blocked — needs product decisions |
| — | **Hard cutover:** deploy `firestore.rules` once both apps' economy paths route through the backend | — | pending |

Units 1–8, 10, 11 done. Only the **analytics** surfaces (unit 9) remain. They are not an
"add an endpoint" job — they read fields the backend no longer writes, and there are **two
different kinds of blocker**:

**(A) Product decision — "revenue".** The dashboard's revenue is `sum(earn_codes.amountMAD)`;
earn codes are points-direct now, so there is no money amount. Revenue must be redefined before
anything is built. Options put to the owner:

1. **Points-based** — points issued / redeemed / gifts / visits / new clients. Buildable now from
   existing backend data; no schema change. *(recommended default)*
2. **Add a price field** — put `priceMAD` on the catalog/earn so real revenue can be summed. Larger:
   backend schema + admin editor input.
3. **Defer** — leave Dashboard/Logs/RewardLogs on direct Firestore; they break at the cutover.

**(B) Backend field gap — cashier attribution (not fixable by a decision).** Per-cashier
scan/redeem stats relied on `cashierName`/`createdByName` written onto each code. The backend does
**not** record which cashier created an earn code or completed a redeem. Rebuilding that panel
needs a **new backend field** (e.g. `earn_codes.createdByUid`, `redeem_codes.completedByUid`) +
an aggregation endpoint — no client-side or decision-only workaround exists.

**Also:** Logs / RewardLogs enumerate `earn_codes`/`redeem_codes` globally — no backend list
endpoint, and the canonical replacement is the per-user activity feed, not a global code dump.

Flagged rather than guessed. Once (A) is chosen (and (B) scoped in or out), unit 9 = one analytics
endpoint + migrating `DashboardRepository` / `LogsRepository` / `RewardLogsRepository`.
