# Pocket QR Pay — Feature Specification

| | |
|---|---|
| **Product** | Pocket |
| **Feature** | QR-based UPI payment initiation + automatic expense categorization |
| **Platform** | Android (Kotlin, Jetpack Compose, Room) |
| **Version** | 2.1 (supersedes 2.0 and `pocket-qr-pay-implementation.md` v1; 2.1 changes are listed in Appendix A) |
| **Status** | Ready to implement, **gated by Phase 0 (§5)**. Nothing after Phase 0 is committed until the feasibility spike passes. |
| **Last updated** | 2026-09-21 |

**Keywords.** MUST / MUST NOT / SHOULD / MAY are used in their RFC 2119 sense. Anything marked **[VERIFY]** depends on external behavior that must be confirmed on real devices or against the current NPCI/provider documentation before release.

---

## 1. Feature Summary

Pocket QR Pay lets a user scan a merchant UPI QR, confirm the amount, pick a category, and hand the payment to their UPI app. Pocket records the expense only according to the confidence it actually has that the payment happened.

```text
Pocket Home
  │  tap Scan
  ▼
┌──────────────────────┐
│ SCREEN 1: QR Scanner │  camera → decode → parse
└──────────┬───────────┘
           ▼
┌──────────────────────┐
│ SCREEN 2: Payment    │  merchant · amount · category
│ Setup                │  [ Pay Securely ]
└──────────┬───────────┘
           │
           ├─ default payment app set ────────────────┐
           │                                          │
           ▼ no default                               │
┌──────────────────────┐                              │
│ PAYMENT-APP OVERLAY  │  Google Pay · Paytm · PhonePe│
│ (bottom sheet)       │  [ ] Set as default          │
└──────────┬───────────┘                              │
           │ user picks an app                        │
           └───────────────────┬──────────────────────┘
                               │ persist PaymentAttempt, then launch
                               ▼
   UPI app (native UI, PIN, bank)         ← Pocket has no control here
           │
           ▼
   Return to Pocket (or not)
           │
           ▼
   Result resolution
     ├─ clean, trusted SUCCESS ─────► expense recorded (+ Undo)
     ├─ explicit FAILURE ───────────► nothing recorded
     └─ anything else ──────────────► "Did this payment go through?" (user decides)
```

**Core principle:** *the UPI callback is evidence, not truth.* Pocket auto-records only when the evidence is strong and comes from an app whose behavior has been verified. In every other case the user confirms with one tap.

Pocket does **not** process payments, handle UPI PINs, talk to banks, or settle money. The UPI app owns all of that.

**Choosing the UPI app:** tapping **Pay Securely** either opens an overlay listing Google Pay, Paytm and PhonePe (when the user has no default app), or goes straight to the user's default app (when they have set one). Users who prefer flexibility never set a default and simply see the overlay every time (§12).

---

## 2. Goals and Non-Goals

### Goals

| ID | Goal |
|---|---|
| G1 | Scan → categorize → pay in **two Pocket screens**. |
| G2 | Never record an expense for a payment that did not happen. |
| G3 | Never lose a payment that did happen (including when the user closes the UPI app without returning). |
| G4 | Stay local-first: no Pocket backend, no analytics, no `INTERNET` permission. |
| G5 | Let the user pay with Google Pay, Paytm or PhonePe: choose each time, or set one as the default to skip the choice. |
| G6 | Make the technical unknowns explicit and testable (Phase 0) rather than assumed. |

### Non-goals (V1)

- Pocket is **not** a UPI app, TPAP, PSP or merchant. It must not present itself as one.
- No UPI PIN, bank credential or card handling.
- No server-side or bank-side payment verification.
- No SMS reading, notification listening or accessibility-service scraping to detect payments.
- No collect requests, contact-to-contact transfers, bank transfers, mandates, refunds.
- No pixel-cloning of Google Pay / PhonePe / BHIM UI. Familiar *interaction pattern*, Pocket's own visual identity.
- No iOS.

---

## 3. Assumptions About the Existing Pocket Codebase

This spec was written without access to the Pocket source. Names below are placeholders; adapt them to the real schema and keep the *rules*, not the names.

| Assumption | If false |
|---|---|
| Kotlin + Compose + MVVM + Room | Adapt layer names. |
| Money is stored as integer paise (`Long`), INR only | Keep the same rule for `payment_attempts`. |
| A `transactions` table exists with `type`, `amount_paise`, `category_id`, `merchant`, `note`, `transaction_at`, `source` | Add missing columns in the migration (§24). |
| A `categories` table with stable identifiers for the six default categories (e.g. a `system_key`) | Add a stable key before implementing §11.2. |
| A `payment_methods` table exists | Skip §14.5. |
| A key-value settings store (DataStore / SharedPreferences) exists | Add one for `default_upi_package` (§12.5). |
| A JSON backup/restore exists with a schema version | Follow §23.4 when extending it. |
| The app has no `INTERNET` permission | Keep it that way. |

---

## 4. Technical Boundary and Sources of Truth

### 4.1 What Pocket is in the UPI model

Pocket is a **payment initiator**. It hands a `upi://pay` deep link to a UPI application installed on the device. That application (the payer app) shows its own payment UI, authenticates the user, and talks to the bank.

### 4.2 Sources of truth

| Topic | Authoritative source |
|---|---|
| `upi://pay` request parameters and response parameters | **NPCI UPI Linking Specification** (record the exact version and retrieval date in Appendix E) |
| Google Pay specifics: package name, response shape, Android 11 `<queries>` | Google Pay for India developer docs (Appendix E) |
| Behavior of PhonePe, Paytm, BHIM, bank apps | **Phase 0 measurements**, not documentation |

Google's Android integration docs are written for **verified merchants** with PSP/bank status APIs. They are useful for the mechanics (package name, response fields) but they are **not** evidence that an arbitrary non-merchant app is permitted or able to complete payments through Google Pay. Phase 0 exists to settle that empirically. Google's own page also notes that NPCI expects the generic UPI intent to be supported alongside any app-specific call; Pocket therefore always keeps the generic path available (§12).

### 4.3 Known constraints (design inputs)

| # | Constraint | Consequence in this spec |
|---|---|---|
| C1 | A UPI app may **refuse** an intent payment it considers risky (for example unsigned or non-merchant-verified requests). Behavior differs per app and per version and changes over time. | Phase 0 gate (§5), per-app profiles with deny flags (§12.3), Plan B (§5.5). |
| C2 | Response shape differs per app: flat extras (`Status`, `txnRef`…), a `response` key-value string, or a JSON `tezResponse` (Google Pay). Status casing and success `responseCode` values differ. | Format-tolerant normalizer (§16.2). Never depend on `responseCode`. |
| C3 | A result may never arrive: user closes the UPI app, returns via Recents, or Pocket's process is killed. | Reconciler (§19.3), UNRESOLVED state (§15). |
| C4 | A returned `SUCCESS`/`SUBMITTED` is **unverified** without PSP/bank status APIs. | Auto-record only for trusted apps; otherwise user confirms (§16.4). |
| C5 | A back-press or app close returns the same "cancelled" result as a genuine cancel, including after a successful payment. **[VERIFY]** in Phase 0 per app. | "Cancelled with no data" is never treated as "not paid" (§16.3). |
| C6 | Merchant QRs may carry a signature (`sign`, `mode`, `orgid`, …). Altering the request can invalidate it. | Pass-through launch plan (§13.1). |
| C7 | Android 11+ package visibility limits which UPI apps Pocket can *enumerate* (launching is unaffected). | Intent-based `<queries>` (§21). |

---

## 5. Phase 0 — Feasibility Gate (MUST complete before Phases 1–8)

### 5.1 Purpose

Answer one question with evidence: **can Pocket launch real UPI payments through real UPI apps, and can it tell what happened?** Everything downstream (default app, auto-record policy, deny list, copy) is configured from the answer.

### 5.2 Spike harness

A **debug-build-only** screen (`src/debug/`, never in release) that:

1. accepts a pasted UPI URI or a scanned QR,
2. lets the tester pick: *direct package* / *system chooser*,
3. optionally overrides the amount (to exercise rebuild vs pass-through),
4. launches via `ActivityResultContracts.StartActivityForResult`,
5. logs a **sanitized** record: result code, extra **keys** present, normalized status, `txnRef` echoed or not, elapsed time, whether Pocket regained focus on its own. No VPAs or full URIs in the exported log.

### 5.3 Test matrix

| Dimension | Values |
|---|---|
| UPI apps | **Required:** Google Pay, Paytm, PhonePe (the V1 overlay apps). **Optional:** BHIM and ≥1 bank-issued UPI app, to exercise the "Other UPI app" path |
| QR types | (a) personal static, (b) merchant static without `am`, (c) merchant dynamic with `am` (and `sign` if obtainable), (d) QR with `mam` |
| Launch path | direct `setPackage` (the three overlay apps), `Intent.createChooser` ("Other UPI app") |
| Outcomes to induce | complete payment; cancel before PIN; wrong PIN / decline; **press Back after the success screen**; **close from Recents after success**; kill Pocket (`adb shell am kill`) during payment; airplane mode mid-payment |
| Devices | ≥3 physical devices; ≥1 aggressive-memory OEM skin; ≥2 Android versions incl. latest |

Use ₹1 payments to real recipients you control. Never test with someone else's merchant account.

### 5.4 Record per combination (Appendix D template)

Launch accepted? Blocked (message)? Prefilled correctly? Result code? Extras keys? Status string and casing? `txnRef` echoed? Amount/VPA echoed? Returned to Pocket automatically? Result on Back-after-success? Result on Cancel?

### 5.5 Decision

| Outcome | Criteria | Action |
|---|---|---|
| **GO** | ≥1 of the three overlay apps completes types (a) and (b) end-to-end, and its result behavior is characterized | Proceed. Populate `UpiAppProfile` (§12.3). |
| **GO-RESTRICTED** | Some apps block the intent or return unusable results | Proceed; mark those `blocked = true` or `autoRecordTrusted = false`; the overlay shows blocked apps as unavailable (§12.4). |
| **PLAN B — Scan & Track** | No app completes a prefilled payment | Keep Screens 1–2 and the whole confirmation/reconciliation machinery. Replace the launch with "open the user's UPI app" (`getLaunchIntentForPackage`, which needs the package `<queries>` entries in §21); the user pays there, then returns and answers the same "Did this payment go through?" sheet. All attempts are UNRESOLVED-by-design. Everything in §14–§19 still applies. |

**Exit criteria:** Appendix D filled in; `UpiAppProfile` list and the response-parsing table (§16.2) finalized from measurements; written GO / GO-RESTRICTED / PLAN B decision recorded in Appendix A.

---

## 6. UX Scope and Navigation

Before payment handoff Pocket has **two primary screens**:

| # | Screen | Type |
|---|---|---|
| 1 | QR Scanner | Full screen |
| 2 | Payment Setup | Full screen |
| — | Payment-app overlay (whenever no default app is set) | Bottom sheet on Screen 2 (§12) |
| — | Result / confirmation sheets | Bottom sheets (§17) |
| — | Payments to Confirm | Secondary list screen, off the critical path (§17.4) |

Rules:

- Merchant, amount, category and app choice MUST NOT be separate full screens.
- **Entry point:** a Scan action in the Home top app bar. QR Pay MUST NOT become a permanent bottom-navigation destination in V1.
- Back from Screen 2 returns to Screen 1 (scanner restarts). Back from Screen 1 returns to Home. Neither creates a PaymentAttempt.
- Pocket MUST NOT render a fake UPI-app UI or imitate Google Pay's payment screen.

---

## 7. Screen 1 — QR Scanner

### 7.1 Purpose

Read a UPI QR **locally** and hand a validated `ScannedUpiPayment` to Screen 2.

### 7.2 Layout

```text
┌─────────────────────────────┐
│ ×                       ⚡  │   close · torch
│                             │
│       Scan merchant QR      │
│   Point your camera at a    │
│        UPI QR code          │
│                             │
│      ┌─────────────┐        │
│      │      QR     │        │   large high-contrast frame
│      └─────────────┘        │
│                             │
│    [ Choose from gallery ]  │
└─────────────────────────────┘
```

Dark camera surface, Pocket green accent on the frame. No other controls.

### 7.3 Technology (decided, not "a maintained scanner")

| Concern | Decision |
|---|---|
| Camera | CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) |
| Decoding | ML Kit Barcode Scanning, **bundled model** variant, restricted to `FORMAT_QR_CODE`. The bundled variant works offline from first launch; the unbundled variant depends on Google Play services fetching a module and MUST NOT be used. |
| Analyzer | `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`, background executor |
| Gallery | Android Photo Picker (`PickVisualMedia`) → decode with ML Kit. **No storage/media permission.** |
| Torch | `CameraControl.enableTorch`; hide the button if the device has no flash unit |
| Manifest | `<uses-feature android:name="android.hardware.camera" android:required="false"/>` so camera-less devices can still install and use gallery scanning |

Use the latest stable versions at implementation time; pin them in the version catalog.

### 7.4 Behavior

1. Request `CAMERA` only when the scanner is first opened (not at app start).
2. Bind camera to the composable's lifecycle; unbind on leaving (no frames processed off-screen).
3. For each decoded QR: run `UpiQrParser` (§8).
4. **Valid UPI QR** → stop analysis, haptic tick, navigate to Screen 2 with the parsed payment. Guard against multiple detections firing navigation twice.
5. **Not a UPI QR** → non-blocking hint ("This isn't a UPI payment QR"), keep scanning, throttle the hint to once per 1.5 s.
6. **UPI QR but invalid** (§8.3) → specific message, keep scanning.
7. Scanning a QR never creates any database row.

### 7.5 States

| State | UI |
|---|---|
| Permission not yet asked | Scanner with system prompt |
| Permission denied (can ask again) | Explanation + [Allow camera] + [Choose from gallery] |
| Permission permanently denied | Explanation + [Open settings] + [Choose from gallery] |
| Camera unavailable / error | "Pocket couldn't access the camera." + [Choose from gallery] |
| No camera hardware | Gallery-only mode |

Every state offers gallery scanning so the feature is never a dead end.

---

## 8. QR Parsing and Validation

### 8.1 Request parameters

Parameters are defined by the NPCI UPI Linking Specification **[VERIFY against current version]**.

| Param | Meaning | Pocket handling |
|---|---|---|
| `pa` | Payee address (VPA) | **Required.** Validated (§8.3). |
| `pn` | Payee name | Display as **unverified** text. |
| `mc` | Merchant category code | Keep; drives `QrKind` and optional category hint (§11.4). |
| `tr` | Transaction reference | Preserve; used for correlation if present (§16). |
| `tid` | Transaction ID | Preserve, forward untouched. |
| `tn` | Transaction note | Preserve; display read-only if present. |
| `am` | Amount | Prefill **and lock** (§9.3). |
| `mam` | Minimum amount | Preserve; enforce on user-entered amounts. |
| `cu` | Currency | INR or absent. Anything else → reject. |
| `url` | Reference URL | Preserve; **never open**. |
| `mode`, `orgid`, `purpose`, `sign` | Signed/merchant-intent metadata | **Preserve verbatim** in pass-through. `sign` is dropped only if Pocket must change the amount (§13.2). |
| anything else | — | Preserved verbatim in the launched request; not interpreted. |

> v1 said unknown parameters "may be ignored safely." That is only true for *reading*. For *launching*, dropping them can break the request. They are preserved.

### 8.2 Output model

```kotlin
enum class QrKind { PERSONAL, MERCHANT_STATIC, MERCHANT_DYNAMIC }

data class ScannedUpiPayment(
    val rawUri: String,               // exact trimmed payload — SENSITIVE, never logged
    val payeeVpa: String,             // as scanned
    val payeeVpaKey: String,          // lowercase, for lookups/memory/duplicate checks
    val payeeName: String?,
    val merchantCategoryCode: String?,
    val qrTransactionRef: String?,
    val qrNote: String?,
    val qrReferenceUrl: String?,
    val qrAmountPaise: Long?,         // null when absent or zero
    val minAmountPaise: Long?,        // from `mam`
    val hasSignature: Boolean,        // `sign` present
    val kind: QrKind
)
```

`kind` (heuristic, for UI labels and the Phase 0 matrix only): no `mc` or `mc == "0000"` → `PERSONAL`; `mc` present and (`am` or `sign` present) → `MERCHANT_DYNAMIC`; otherwise `MERCHANT_STATIC`.

### 8.3 Validation rules

| Rule | On failure |
|---|---|
| Payload length 1…2048 characters after trim | `INVALID: TOO_LONG` |
| Head equals `upi://pay` (case-insensitive) | Not a UPI QR (silent hint) |
| Exactly one each of `pa`, `am`, `cu`, `tr` if present (no duplicates) | `INVALID: DUPLICATE_PARAM` |
| `pa` matches `^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,63}$` | `INVALID: BAD_VPA` |
| `cu` absent or `INR` (case-insensitive) | `INVALID: NON_INR` |
| `am` empty or zero → treated as **absent** | — |
| `am` non-empty, non-zero: matches `^\d{1,9}(\.\d{1,2})?$` and ≥ 0.01 | `INVALID: BAD_AMOUNT` |
| Percent-decoding succeeds for all values | `INVALID: MALFORMED` |
| No control characters in `pn`, `tn` | `INVALID: MALFORMED` |

Amounts are converted with `BigDecimal` (never `Double`/`Float`) and must fit Pocket's existing paise range.

### 8.4 Parser sketch (pure Kotlin, JVM-testable)

Deliberately avoids `android.net.Uri` so it can be unit-tested without Robolectric and so the **raw encoded query is preserved byte-for-byte** for pass-through and rebuild.

```kotlin
internal data class QueryPair(val rawKey: String, val rawValue: String) {
    val key: String get() = decode(rawKey).lowercase(Locale.ROOT)
    val value: String get() = decode(rawValue)
    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8") // IllegalArgumentException on bad %
}

internal fun splitQuery(rawQuery: String): List<QueryPair> =
    if (rawQuery.isEmpty()) emptyList()
    else rawQuery.split('&').filter { it.isNotEmpty() }.map {
        QueryPair(it.substringBefore('='), it.substringAfter('=', ""))
    }

object UpiQrParser {
    private const val MAX_PAYLOAD = 2048
    private val VPA = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,63}$")
    private val AMOUNT = Regex("^\\d{1,9}(\\.\\d{1,2})?$")

    fun parse(payload: String): ParseResult {
        val raw = payload.trim()
        if (raw.isEmpty() || raw.length > MAX_PAYLOAD) return ParseResult.Invalid(Reason.TOO_LONG)
        if (!raw.substringBefore('?').equals("upi://pay", ignoreCase = true)) return ParseResult.NotUpi

        val pairs = try { splitQuery(raw.substringAfter('?', "")) }
                    catch (e: IllegalArgumentException) { return ParseResult.Invalid(Reason.MALFORMED) }

        fun single(k: String): String? {
            val hits = pairs.filter { it.key == k }
            return if (hits.size > 1) throw DuplicateParam() else hits.firstOrNull()?.value
        }
        return try {
            val pa = single("pa")?.trim().orEmpty()
            if (!VPA.matches(pa)) return ParseResult.Invalid(Reason.BAD_VPA)
            val cu = single("cu")
            if (cu != null && !cu.equals("INR", ignoreCase = true)) return ParseResult.Invalid(Reason.NON_INR)

            val amount = when (val am = single("am")?.trim()) {
                null, "" -> null
                else -> {
                    if (!AMOUNT.matches(am)) return ParseResult.Invalid(Reason.BAD_AMOUNT)
                    val paise = BigDecimal(am).movePointRight(2).longValueExact()
                    if (paise == 0L) null else paise
                }
            }
            // …build ScannedUpiPayment (mc, tr, tn, url, mam, sign, kind)
            ParseResult.Valid(/* … */)
        } catch (e: DuplicateParam) { ParseResult.Invalid(Reason.DUPLICATE_PARAM) }
          catch (e: ArithmeticException) { ParseResult.Invalid(Reason.BAD_AMOUNT) }
    }
}
```

---

## 9. Screen 2 — Payment Setup

The central Pocket screen. One screen, no sub-navigation.

### 9.1 Layout

```text
┌─────────────────────────────────┐
│ ←                               │
│                                 │
│  Paying                         │
│  Sri Krishna Stores             │
│  sri.krishna@okhdfcbank         │
│  Name comes from the QR and     │
│  isn't verified.                │
│                                 │
│  Amount                         │
│  ₹450.00        🔒 set by QR    │
│                                 │
│  Category                       │
│  ┌─────────┬─────────┬────────┐ │
│  │  Food   │Groceries│Transport│ │
│  ├─────────┼─────────┼────────┤ │
│  │Shopping │  Bills  │ Other  │ │
│  └─────────┴─────────┴────────┘ │
│  + Add a note                   │
│                                 │
│  [        Pay Securely        ] │
│  Paying with Google Pay · Change│
└─────────────────────────────────┘
```

### 9.2 Design rules

- Merchant name and **full VPA** are always visible. The name is shown as unverified (fake-QR-sticker fraud is common); the caption is small and permanent, not a modal.
- Amount is the dominant numeric element, formatted `en-IN`.
- Six categories are visible without scrolling on a 360 dp-wide, 640 dp-tall screen at default font scale. At large font scales the screen scrolls; the CTA remains pinned.
- The note is **local to Pocket only** (never sent to the UPI app; §13). It is collapsed by default.
- CTA is pinned to the bottom, one-thumb reachable. Its label is always **Pay Securely**; it never names an app.
- The caption "Paying with {App} · Change" under the CTA is shown **only when a default payment app is set** (§12.5). With no default, nothing is shown there.
- Follows Pocket's cream/off-white + muted green system; supports dark mode.
- No separate "choose payment app" *page*: app choice is an overlay opened by the CTA when no default is set (§12).

### 9.3 Amount rules

| Case | Behavior |
|---|---|
| QR has a valid amount | Shown **read-only** with a lock affordance and the caption "Set by the merchant". The launch is a pass-through of the original request (§13.1). |
| QR has no amount | Numeric keypad field, required. Validated per §10. If `mam` is present, amount MUST be ≥ `mam` ("Minimum amount is ₹X"). |

> v1 allowed editing a QR-supplied amount. That is removed: changing `am` on a merchant/dynamic QR can invalidate its signature or under-pay the merchant's order, and UPI apps themselves lock such amounts.

### 9.4 CTA state

| Condition | CTA |
|---|---|
| Amount invalid or missing | Disabled. Inline error under amount. |
| No category selected (and none pre-selected) | Disabled. Helper: "Choose a category". |
| Ready | Enabled: **Pay Securely** |
| Tap | Runs the pre-launch checks (§9.5), then either opens the payment-app overlay (no usable default) or launches the default app directly (§12.2). The CTA is disabled synchronously and stays disabled while a launch is in progress (single-flight, §18); if the overlay is dismissed it is enabled again |
| Default app is unusable (not installed or blocked) | Default is cleared; the overlay opens with a notice (§12.5) |

### 9.5 Pre-launch warnings (non-blocking interstitials)

Shown as a dialog after the CTA is tapped, **before** the payment-app step (§12.2) and before any attempt is created:

1. **Unresolved attempt to the same payee** (same `payeeVpaKey`, any amount, status `UNRESOLVED`, last 24 h): "You have an unconfirmed payment to {merchant}. Confirm it first to avoid paying twice." → [Review it] [Pay anyway]
2. **Same payee and same amount recorded in the last 2 minutes** (any source): "You paid ₹450 to {merchant} moments ago." → [Cancel] [Pay again]
3. **Amount unusually large** (> ₹1,00,000): "UPI limits differ by bank and app; your UPI app will enforce them." → [Continue] (informational only; Pocket does not enforce UPI limits.)

### 9.6 States

`Ready` · `AmountInvalid(reason)` · `CategoryMissing` · `ChoosingApp` · `Launching` · `LaunchFailed(reason)`. The scanned payment is held in the ViewModel's `SavedStateHandle` so rotation/process recreation before tapping Pay does not lose it.

---

## 10. Amount and Money Rules

- Store **integer paise** (`Long`). Never persist floating-point money.
- INR only. At most two decimal places. Amount > 0.
- Parse user input with `BigDecimal`; reject more than two decimals rather than rounding.
- Display uses `en-IN` grouping (`₹1,23,456.78`) via `NumberFormat`/`DecimalFormat` with an explicit `Locale("en", "IN")`.
- **The UPI request MUST NOT use display formatting or the default locale.** Always serialize with:

```kotlin
fun paiseToUpiAmount(paise: Long): String {
    require(paise > 0)
    return BigDecimal.valueOf(paise, 2).toPlainString()   // 45000 → "450.00"; locale-independent
}
```

A test MUST set the default locale to one with non-ASCII digits or comma decimals (e.g. `ar`, `de`) and assert the output is unchanged.

| Input | Stored (paise) | Displayed | Sent as `am` |
|---|---:|---|---|
| `250` | 25000 | ₹250.00 | `250.00` |
| `250.5` | 25050 | ₹250.50 | `250.50` |
| `123456.78` | 12345678 | ₹1,23,456.78 | `123456.78` |
| `0.001` | — | rejected | — |

---

## 11. Categories

### 11.1 Six quick categories

| # | Label | Stable key |
|---|---|---|
| 1 | Food | `food` |
| 2 | Groceries | `groceries` |
| 3 | Transport | `transport` |
| 4 | Shopping | `shopping` |
| 5 | Bills | `bills` |
| 6 | Other | `other` |

### 11.2 Mapping to the user's categories

Map by **stable key**, never by display name. Do not create duplicate categories. If a mapped category was deleted, hide its tile and show the remaining ones (the grid tolerates fewer than six); if `other` is missing, recreate it. A category referenced by an `UNRESOLVED` attempt MUST NOT be hard-deleted (block the delete with an explanation, or use soft-delete if Pocket has it).

### 11.3 Pre-selection (this replaces v1's "never infer")

Priority order:

1. **Merchant memory:** the category last used for this `payeeVpaKey` (§14.3). Shown selected, with a small "Last used" caption; one tap changes it.
2. **MCC hint** (§11.4), only if there is no memory. Shown selected with a "Suggested" caption.
3. Nothing pre-selected → the user must choose; CTA disabled until they do.

Result: the second payment to the same shop is *scan → Pay Securely* (and, with a default app set, straight into the UPI app).

Selection is never conveyed by color alone (check icon + border + accessibility state).

### 11.4 MCC hint table (optional, V1.1; ship behind a constant so it can be edited)

| MCC | Category |
|---|---|
| 5411, 5422, 5441, 5451, 5499 | Groceries |
| 5812, 5813, 5814 | Food |
| 4111, 4121, 4131, 4789, 5541, 5542 | Transport |
| 4900, 4814, 4899 | Bills |
| 5311, 5651, 5691, 5699, 5732 | Shopping |
| anything else | no hint |

Illustrative; finalize with real merchant QRs during Phase 3.

---

## 12. Payment App Selection

### 12.1 Principle

There is one CTA: **Pay Securely**. What it does next depends on one stored preference, the **default payment app**.

| Default payment app | Tap "Pay Securely" |
|---|---|
| **Not set** ("Ask me every time") | The **payment-app overlay** opens **every time**. The user picks an app for this payment. |
| **Set** and usable | Pocket launches that app **directly**. **No overlay.** |
| **Set** but unusable (uninstalled or blocked) | The default is cleared and the overlay opens with a notice (§12.5). |

So a user has two ways to use Pocket:

- **A. No default.** They see the overlay on every payment and can pay with a different app each time.
- **B. One default.** They pick one app once; every later payment goes straight to it.

V1 supports a fixed list of **three apps: Google Pay, Paytm, PhonePe**, in that order (order never changes with usage). **No app is the default out of the box**: Google Pay has no special status and is not preselected. A small "Other UPI app" link (§12.4) covers everyone else.

### 12.2 Flow (decision procedure)

```text
Tap "Pay Securely"
   │
   ▼
Validate amount + category ────── invalid ──► inline errors, stay
   │
   ▼
Pre-launch warnings (§9.5) ────── cancel ───► stay on Screen 2
   │
   ▼
Resolve payment app  (ResolvePaymentAppUseCase)
   ├─ default set AND installed AND not blocked ──► use it       (NO overlay)
   ├─ default set BUT unusable ──► clear default ──► overlay + notice
   └─ no default ─────────────────────────────────► overlay
                                                       │
                          user taps an app row ◄───────┤
                     (optionally with "Set as default" │ dismiss / Back / scrim
                      ticked)                          ▼
                          │                      nothing happens:
                          ▼                      no attempt, CTA re-enabled
                 createAttempt → launch (§19.1)
```

`ResolvePaymentAppUseCase` is a pure decision function. Its table is the unit-test target (§27.1):

| Stored default | Installed | Blocked | Outcome |
|---|---|---|---|
| none | – | – | `ShowOverlay` |
| a supported package | yes | no | `Launch(package)` |
| a supported package | no | – | `ClearDefault` + `ShowOverlay(notice = Unavailable(app))` |
| a supported package | yes | yes | `ClearDefault` + `ShowOverlay(notice = Unavailable(app))` |
| an unknown / corrupt value | – | – | `ClearDefault` + `ShowOverlay` (no notice) |

### 12.3 Supported apps and `UpiAppProfile`

```kotlin
data class UpiAppProfile(
    val packageName: String,
    val displayName: String,
    val blocked: Boolean = false,            // intent payments refused/unusable (Phase 0)
    val autoRecordTrusted: Boolean = false,  // clean SUCCESS may auto-record (Phase 0)
    val resultFormat: Set<ResponseFormat> = emptySet(),
    val minTestedVersion: String? = null,
    val notes: String? = null
)

object SupportedUpiApps {
    // Order = overlay order. Verify package names on real devices in Phase 0.
    val all: List<UpiAppProfile> = listOf(
        UpiAppProfile("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        UpiAppProfile("net.one97.paytm", "Paytm"),
        UpiAppProfile("com.phonepe.app", "PhonePe"),
    )
}
```

- `blocked` and `autoRecordTrusted` are set **from Phase 0 measurements** and default to the safe values (`false`).
- Apps reached through the **"Other UPI app"** link are launched with `Intent.createChooser`; the handling app is unknown, so they have no profile and are **never** auto-recorded (§16.6).
- The list and flags are updated **per Pocket release** (no remote config). Note this in the release checklist (§29).
- Adding a fourth app later (e.g. BHIM) means adding one profile and one `<queries>` entry (§21); no flow changes.

### 12.4 The overlay: PAY mode

Opened by **Pay Securely** when no usable default exists, and by **Try another UPI app** after a failure (§17.5).

```text
┌─────────────────────────────────┐
│               ───               │   drag handle
│  Pay ₹450 to Sri Krishna Stores │
│  You'll complete the payment    │
│  in your UPI app.               │
│                                 │
│  ┌───┐                          │
│  │ ▣ │  Google Pay              │
│  └───┘                          │
│  ┌───┐                          │
│  │ ▣ │  Paytm                   │
│  └───┘                          │
│  ┌───┐                          │
│  │ ▣ │  PhonePe                 │
│  └───┘                          │
│                                 │
│  ☐  Set as default payment app  │
│                                 │
│           Other UPI app         │
└─────────────────────────────────┘
```

**Rules**

- A bottom sheet over Screen 2 with a dimmed scrim. It is **not** a new screen (§6).
- **Dismiss** (drag, scrim tap, system Back) does nothing: no attempt is created and the CTA is enabled again.
- The header repeats the payee and amount for context. The subtitle states that the UPI app completes the payment; this is what makes the "Pay Securely" label honest (§29).
- The three rows are always shown, in fixed order, each with the app's **own launcher icon** loaded from `PackageManager` (a neutral placeholder if it isn't installed).

**Row states**

| State | Appearance | Behavior |
|---|---|---|
| Available | Icon + name | Tappable |
| Default | Adds a "Default" badge | Tappable. Only seen when a default exists but the overlay was opened via "Try another UPI app" |
| Not installed | Dimmed; caption "Not installed" | Disabled |
| Unavailable | Dimmed; caption "Can't be used with Pocket right now" | Disabled (`blocked = true` from Phase 0) |

**Tapping an available row**

1. If **Set as default payment app** is ticked, persist that app as the default (§12.5).
2. Disable all rows synchronously (single-flight, §18).
3. Continue to `createAttempt` → launch (§19.1) using **direct package launch** (`setPackage`).

**"Set as default payment app" checkbox**

- **Unticked** every time the overlay opens (a default is never set by accident).
- Its state lives only as long as the overlay (kept across rotation, §19.5).
- Hidden when no row is available.
- Ticking it and choosing an app both **launches this payment** and **sets the default**. Not ticking it never changes an existing default.

**"Other UPI app" link**

- Small, low-emphasis, below the checkbox. Launches `Intent.createChooser` (provider = `CHOOSER`, `provider_package = null`).
- Ignores the checkbox: a default can only be one of the three listed apps.
- Kept because NPCI expects the generic intent to remain available and because users whose only UPI app is BHIM or a bank app would otherwise have no way to pay. It can be hidden with the constant `SHOW_OTHER_UPI_APP_LINK = false` if product decides V1 must be strictly three apps, but see §12.7 for the consequence.

### 12.5 The default payment app preference

**Storage.** A single nullable string, `default_upi_package`, in Pocket's existing settings store (DataStore / SharedPreferences), **not** in Room. Valid values are the package names in `SupportedUpiApps`; anything else is read as "not set" and cleared. It is **not** included in backups: which apps exist is device-specific (§23.4).

**Set**

- Ticking "Set as default payment app" and choosing a row in PAY mode.
- Choosing an app in MANAGE mode (§12.6).

**Cleared**

- "Ask me every time" in MANAGE mode.
- Automatically when the default is unusable at Pay time (uninstalled or `blocked`), in which case the overlay opens with the notice **"{App} isn't available. Choose another app."**
- When a direct launch throws `ActivityNotFoundException` (§13.4).

**What the user sees with a default set**

- The CTA stays **Pay Securely**. Tapping it goes straight to the UPI app.
- A caption under the CTA reads **"Paying with Google Pay · Change"** so the choice is never invisible. "Change" opens MANAGE mode. With no default, nothing is shown under the CTA.
- Settings → **Default payment app** shows the current value and opens MANAGE mode.

### 12.6 The overlay: MANAGE mode

Opened by **Change** (under the CTA) and by **Settings → Default payment app**. It changes the preference only. **It never starts a payment and never creates an attempt.**

```text
┌─────────────────────────────────┐
│  Default payment app            │
│                                 │
│   ○  Ask me every time          │
│   ●  Google Pay                 │
│   ○  Paytm                      │
│   ○  PhonePe                    │
└─────────────────────────────────┘
```

- Radio-style; the current value is selected.
- Selecting a row saves immediately and closes the sheet. A snackbar confirms: "Google Pay is now your default" or "Pocket will ask each time".
- Not-installed and unavailable rows are disabled and cannot be selected.
- The caption under the CTA appears or disappears immediately.

### 12.7 Detection and availability

- For each supported app, `installed` means the package is visible (via the `<queries>` entries, §21) and `setPackage(pkg)` resolves an `ACTION_VIEW` on `upi://pay`. `usable = installed && !blocked`.
- Evaluate **fresh** when the overlay opens and again at Pay time; never cache across payments (apps get installed and uninstalled).
- **None of the three is usable:** the overlay still opens (all rows disabled) so the user understands why, and "Other UPI app" is the only action. If the link is hidden by config, or no app on the device handles `upi://pay` at all, show the dialog `no_upi_app` and create no attempt.
- **Plan B** (§5.5) uses the same overlay and the same preference; only the launch differs (open the app instead of a prefilled payment).

### 12.8 Branding

Show apps by **name and their own launcher icon** loaded from `PackageManager`. Do not embed third-party logos or brand marks in Pocket's assets. Follow each provider's brand guidelines for how their name may appear **[VERIFY]**.

---

## 13. Launch Plan and Intent Construction

### 13.1 Two launch plans

| Plan | When | What is sent |
|---|---|---|
| **PassThrough** | QR has a valid amount | The **original scanned URI, unchanged**. |
| **Rebuilt** | QR has no amount (user entered one) | The original query with only the necessary changes (§13.2). |

Rationale: payment-gateway integration guides warn that modifying a provided intent URL can cause payment failure, and merchant QRs can be signed. Pocket therefore edits as little as possible.

### 13.2 Rebuild rules (amount-less QR)

Operate on the **raw encoded query pairs**, not decoded values, so other parameters are forwarded byte-for-byte:

1. Drop `am`, `sign` (a signature over the old request is invalid once `am` is added), and any existing `cu`.
2. Forward **all** other parameters verbatim (`pa`, `pn`, `mc`, `tid`, `tr`, `tn`, `url`, `mam`, `mode`, `orgid`, `purpose`, unknown).
3. Append `am=<paiseToUpiAmount>` and `cu=INR`.
4. If `tr` is absent, append a generated reference: `"PKT" + attemptId.replace("-", "").take(20).uppercase()` (alphanumeric, 23 chars; NPCI limits `tr` length **[VERIFY: currently 35]**).
5. Never add `tn` from Pocket's local note.
6. Never send `null`, empty or "null" strings for absent values: omit the parameter.

> If Phase 0 shows an app rejects a rebuilt request that dropped `sign` on a merchant static QR, record it in that app's profile and consider Plan B for that QR kind.

### 13.3 Reference model

| Reference | Meaning |
|---|---|
| `attemptId` | Pocket's own UUID. The idempotency key for everything in Pocket. Never sent to the UPI app on its own. |
| `sentTxnRef` | The `tr` actually in the launched URI: the QR's `tr` (pass-through) or the generated one (rebuilt). May be `null` for pass-through of a QR with no `tr`. |
| `expectedTxnRef` | `sentTxnRef`, used to correlate the returned `txnRef` (§16.3). |

Pocket MUST NOT rely on `tr` being unique across attempts: static merchant QRs repeat it. Uniqueness lives in `attemptId`.

### 13.4 Building the intent

```kotlin
class UpiIntentFactory(private val ctx: Context) {

    fun direct(uri: String, pkg: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { setPackage(pkg) }

    fun chooser(uri: String, title: CharSequence): Intent =
        Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(uri)), title)
}
```

Rules:

- MUST NOT set `FLAG_ACTIVITY_NEW_TASK` (it breaks result delivery).
- Launch through the Activity Result API (`StartActivityForResult`), registered unconditionally at composition/Activity creation (§19.2).
- Catch `ActivityNotFoundException` and `SecurityException` → `FAILED (LAUNCH_ERROR)`, no expense; if the failing app was the stored default, clear it (§12.5); offer another app.
- Log only `attemptId`, `provider`, `amountPaise`, `plan`. **Never** the URI, VPA or name.
- The `Uri.Builder.appendQueryParameter` pattern from Google's merchant sample is **not used** for pass-through, and MUST NOT be fed nullable values in rebuild (a null can be serialized as the literal text "null").

---

## 14. Data Model

Names are placeholders (§3); the constraints are normative.

### 14.1 `payment_attempts`

```kotlin
@Entity(
    tableName = "payment_attempts",
    indices = [Index("status"), Index("payee_vpa_key"), Index("created_at")]
)
data class PaymentAttemptEntity(
    @PrimaryKey val id: String,                       // UUID = attemptId
    @ColumnInfo(name = "payee_vpa") val payeeVpa: String,
    @ColumnInfo(name = "payee_vpa_key") val payeeVpaKey: String,
    @ColumnInfo(name = "payee_name") val payeeName: String?,
    @ColumnInfo(name = "merchant_category_code") val merchantCategoryCode: String?,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val currency: String,                             // "INR"
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "local_note") val localNote: String?,

    @ColumnInfo(name = "launch_plan") val launchPlan: LaunchPlan,     // PASS_THROUGH | REBUILT
    @ColumnInfo(name = "sent_txn_ref") val sentTxnRef: String?,
    @ColumnInfo(name = "qr_kind") val qrKind: QrKind,
    @ColumnInfo(name = "raw_upi_uri") val rawUpiUri: String?,         // SENSITIVE; nulled at terminal state

    val provider: PaymentProvider,                    // DIRECT_APP | CHOOSER
    @ColumnInfo(name = "provider_package") val providerPackage: String?, // known for DIRECT_APP only

    val status: PaymentAttemptStatus,
    @ColumnInfo(name = "result_hint") val resultHint: ResultHint,
    @ColumnInfo(name = "result_code") val resultCode: Int?,
    @ColumnInfo(name = "app_status") val appStatus: String?,          // normalized SUCCESS|FAILURE|SUBMITTED|NONE
    @ColumnInfo(name = "app_txn_id") val appTxnId: String?,
    @ColumnInfo(name = "app_approval_ref") val appApprovalRef: String?,
    @ColumnInfo(name = "app_response_code") val appResponseCode: String?,

    @ColumnInfo(name = "recorded_source") val recordedSource: RecordedSource?,
    @ColumnInfo(name = "created_at") val createdAt: Long,             // epoch millis, or match Pocket's existing convention
    @ColumnInfo(name = "launched_at") val launchedAt: Long?,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long?,
    @ColumnInfo(name = "last_prompted_at") val lastPromptedAt: Long?,  // §17.1: modal shown at most once
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

> Use whatever timestamp representation the existing schema uses (epoch millis or ISO strings). Do not mix.

### 14.2 Enums

```kotlin
enum class PaymentAttemptStatus {
    CREATED,      // persisted, not yet launched
    LAUNCHED,     // UPI app started; awaiting return
    UNRESOLVED,   // outcome unknown; needs the user (see ResultHint)
    RECORDED,     // expense created            (terminal)
    FAILED,       // explicit failure / launch error (terminal, no expense)
    DISCARDED,    // user said it didn't happen / abandoned (terminal, no expense)
    EXPIRED       // unresolved past retention (terminal, no expense)
}

enum class ResultHint {
    NONE,
    APP_SUBMITTED,          // app said SUBMITTED / pending
    APP_SUCCESS_UNVERIFIED, // app said SUCCESS but app is not trusted / response not clean
    MISMATCH,               // returned amount / VPA / ref disagreed with the attempt
    CANCELED_NO_DATA,       // RESULT_CANCELED with no usable payload (may be a real cancel OR post-success back-press)
    NO_STATUS,              // RESULT_OK but no recognizable status / malformed
    NO_RETURN_DATA,         // user came back to Pocket without any result being delivered
    RESTORED,               // came from a backup restore
    LAUNCH_ERROR            // (with FAILED) the UPI app could not be started
}

enum class RecordedSource { APP_REPORTED, USER_CONFIRMED }
enum class LaunchPlan { PASS_THROUGH, REBUILT }
enum class PaymentProvider { DIRECT_APP, CHOOSER }
```

The four provider-level outcomes users think in map onto these:

| Provider says | Pocket state |
|---|---|
| SUCCESS (clean, trusted) | `RECORDED` (`APP_REPORTED`) |
| SUCCESS (untrusted / unclean) | `UNRESOLVED` + `APP_SUCCESS_UNVERIFIED` / `MISMATCH` |
| FAILURE | `FAILED` |
| SUBMITTED | `UNRESOLVED` + `APP_SUBMITTED` |
| nothing / cancelled-no-data | `UNRESOLVED` + `CANCELED_NO_DATA` / `NO_RETURN_DATA` / `NO_STATUS` |

### 14.3 `merchant_category_memory`

```kotlin
@Entity(tableName = "merchant_category_memory")
data class MerchantCategoryMemory(
    @PrimaryKey @ColumnInfo(name = "payee_vpa_key") val payeeVpaKey: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

Written when an attempt becomes `RECORDED` (either source), never at launch.

### 14.4 `transactions` changes

Add `payment_attempt_id TEXT NULL` with a **UNIQUE index** (SQLite permits many NULLs). No foreign key to attempts (keeps deletes independent). If the user deletes/undoes the expense, the attempt stays `RECORDED`; a `RECORDED` attempt never re-records.

Add `source` values `QR_APP_REPORTED` and `QR_USER_CONFIRMED` (alongside existing values such as `MANUAL`).

Mapping when recording: `type = EXPENSE`, `amount_paise`, `category_id`, `merchant = payeeName ?: payeeVpa`, `note = localNote` (fallback: QR `tn` if present), `transaction_at = now()` (UPI apps do not reliably return a completion time; `launched_at` is retained on the attempt), `payment_attempt_id = attempt.id`.

### 14.5 Payment method

Optionally map `providerPackage` to an existing payment method (Google Pay / Paytm / PhonePe). For `CHOOSER` launches the chosen app is unknown, so use "UPI (other)". Never auto-create payment methods.

### 14.6 Retention

| Data | Retention |
|---|---|
| `raw_upi_uri` | Cleared (`NULL`) the moment the attempt reaches any terminal state |
| Terminal attempts (`FAILED`, `DISCARDED`, `EXPIRED`) | Purged after 90 days |
| `RECORDED` attempts | Kept (small) so the link to the expense remains explainable |
| `UNRESOLVED` | 30 days, then `EXPIRED` |

Evaluated lazily on app foreground; no WorkManager needed.

---

## 15. State Machine

```text
CREATED ──LaunchStarted──────────────► LAUNCHED
   │                                     │
   ├─ LaunchError ────► FAILED           ├─ Decide(RecordAuto) ───────► RECORDED
   └─ StartupCleanup ─► DISCARDED        ├─ Decide(Fail) ─────────────► FAILED
                                         └─ Decide(Unresolve) or
                                            ResumeWithoutResult ──────► UNRESOLVED

UNRESOLVED ── UserConfirmPaid ────────► RECORDED (USER_CONFIRMED)
           ── UserConfirmNotPaid ─────► DISCARDED
           ── Expire (30 days) ───────► EXPIRED
           ── LateResult(clean), only for hints NO_RETURN_DATA /
              CANCELED_NO_DATA ───────► RECORDED (APP_REPORTED) or FAILED

Terminal: RECORDED · FAILED · DISCARDED · EXPIRED
```

### 15.1 Transition table (the only legal transitions)

| # | From | Event | To | Side effects |
|---|---|---|---|---|
| 1 | — | `Create` | CREATED | insert row |
| 2 | CREATED | `LaunchStarted` | LAUNCHED | set `launched_at` |
| 3 | CREATED | `LaunchError` | FAILED (`LAUNCH_ERROR`) | clear raw URI |
| 4 | CREATED | `StartupCleanup` (stale > 60 s) | DISCARDED | clear raw URI |
| 5 | LAUNCHED | `Decide(RecordAuto)` | RECORDED | insert expense (`QR_APP_REPORTED`), update memory, clear raw URI |
| 6 | LAUNCHED | `Decide(Fail)` | FAILED | clear raw URI |
| 7 | LAUNCHED | `Decide(Unresolve(h))` | UNRESOLVED | set hint |
| 8 | LAUNCHED | `ResumeWithoutResult` | UNRESOLVED (`NO_RETURN_DATA`) | (§19.3) |
| 9 | UNRESOLVED (`NO_RETURN_DATA` or `CANCELED_NO_DATA`) | `LateResult(RecordAuto)` | RECORDED | as #5 |
| 10 | UNRESOLVED (same hints) | `LateResult(Fail)` | FAILED | clear raw URI |
| 11 | UNRESOLVED | `UserConfirmPaid` | RECORDED (`USER_CONFIRMED`) | insert expense (`QR_USER_CONFIRMED`), update memory, clear raw URI |
| 12 | UNRESOLVED | `UserConfirmNotPaid` | DISCARDED | clear raw URI |
| 13 | UNRESOLVED | `Expire` | EXPIRED | clear raw URI |

Terminal states (`RECORDED`, `FAILED`, `DISCARDED`, `EXPIRED`) accept **no** events; any attempt is rejected and counted in a debug log. A late result arriving for a terminal attempt is ignored.

### 15.2 Implementation

A pure function `reduce(state, event): Transition?` (returns `null` for illegal) drives the table above and is unit-tested exhaustively (§27.1). Persistence uses **conditional updates** (§18.3) so the database, not memory, is the arbiter.

---

## 16. Result Handling

### 16.1 Inputs

From the Activity Result: `resultCode` (`RESULT_OK`, `RESULT_CANCELED`, other) and the returned `Intent?` extras. Copy only **String-valued** extras into a `Map<String, String>` (keys preserved) and pass them to the normalizer. Never log the map.

### 16.2 Response formats and the normalizer

UPI apps return one or more of these shapes **[VERIFY per app in Phase 0]**:

| Format | Shape | Typical source |
|---|---|---|
| `FLAT_EXTRAS` | Separate extras: `Status`, `txnId`, `responseCode`, `txnRef`, `ApprovalRefNo` | Many apps; Google Pay's older fields (documented as deprecated) |
| `RESPONSE_STRING` | One extra `response` = `txnId=…&responseCode=00&Status=SUCCESS&txnRef=…` | Many apps |
| `TEZ_JSON` | One extra `tezResponse` = JSON, may include `Status`, `txnRef`, `txnId`, `responseCode`, `amount`, `toVpa`; some fields absent on failure | Google Pay |

Additional realities the normalizer MUST tolerate:

- Status casing varies (`SUCCESS`, `Success`, `success`).
- Success `responseCode` values differ per app (e.g. `0`, `00`, …). **Never** make a decision from `responseCode`; store it for diagnostics only.
- `amount` and `toVpa` are only reliably present for some apps (Google Pay). Absence is normal, not a mismatch.
- Different sources inside one result can disagree; that is treated as malformed.

```kotlin
enum class ResponseFormat { FLAT_EXTRAS, RESPONSE_STRING, TEZ_JSON }
enum class UpiOutcome { SUCCESS, FAILURE, SUBMITTED, NONE }

data class NormalizedUpiResult(
    val outcome: UpiOutcome,
    val txnRef: String?, val txnId: String?, val approvalRef: String?, val responseCode: String?,
    val amountPaise: Long?, val toVpa: String?,
    val formats: Set<ResponseFormat>,
    val wellFormed: Boolean
)

object UpiResultNormalizer {
    private val FLAT = setOf("status", "txnid", "responsecode", "txnref", "approvalrefno")

    fun normalize(raw: Map<String, String>): NormalizedUpiResult {
        val f = raw.mapKeys { it.key.lowercase(Locale.ROOT) }
        val sources = mutableListOf<Map<String, String>>()   // precedence: tez > response > flat
        val formats = mutableSetOf<ResponseFormat>()
        var wellFormed = true

        f["tezresponse"]?.let { json ->
            runCatching { JSONObject(json).toLowerKeyStringMap() }
                .onSuccess { sources += it; formats += ResponseFormat.TEZ_JSON }
                .onFailure { wellFormed = false }
        }
        f["response"]?.let { qs ->
            sources += parseKeyValueString(qs).mapKeys { it.key.lowercase(Locale.ROOT) }
            formats += ResponseFormat.RESPONSE_STRING
        }
        f.filterKeys { it in FLAT }.takeIf { it.isNotEmpty() }?.let {
            sources += it; formats += ResponseFormat.FLAT_EXTRAS
        }

        fun pick(name: String): String? {
            val values = sources.mapNotNull { it[name]?.trim()?.ifEmpty { null } }
            if (values.map { it.lowercase(Locale.ROOT) }.distinct().size > 1) wellFormed = false
            return values.firstOrNull()
        }

        val outcome = when (pick("status")?.uppercase(Locale.ROOT)) {
            "SUCCESS", "SUCCEEDED" -> UpiOutcome.SUCCESS
            "FAILURE", "FAILED", "FAIL" -> UpiOutcome.FAILURE
            "SUBMITTED", "PENDING" -> UpiOutcome.SUBMITTED
            else -> UpiOutcome.NONE
        }
        val amountPaise = pick("amount")?.let { parseAmountPaiseOrNull(it) ?: run { wellFormed = false; null } }
        return NormalizedUpiResult(outcome, pick("txnref"), pick("txnid"), pick("approvalrefno"),
            pick("responsecode"), amountPaise, pick("tovpa"), formats, wellFormed)
    }
}
```

The `org.json` classes need a JVM test dependency (e.g. `org.json:json`) for unit tests. `parseAmountPaiseOrNull` reuses the §8 amount rules.

### 16.3 Correlation and validation

| Check | Rule | Failure → |
|---|---|---|
| Correlation | Only one attempt can be `LAUNCHED` (§18.1), so a result is matched to it by state. If both `txnRef` and the attempt's `sentTxnRef` exist they MUST be equal (case-insensitive). | `UNRESOLVED (MISMATCH)` |
| Amount | If the result carries an amount, it MUST equal `amountPaise`. | `UNRESOLVED (MISMATCH)` |
| Payee | If the result carries `toVpa`, it MUST equal `payeeVpa` (case-insensitive). | `UNRESOLVED (MISMATCH)` |
| Absence | A missing `txnRef`/amount/`toVpa` is **not** a failure of the check. | — |

### 16.4 Decision function

```kotlin
sealed interface Decision {
    object RecordAuto : Decision
    object Fail : Decision
    data class Unresolve(val hint: ResultHint) : Decision
    object Ignore : Decision
}

private val LATE_OK = setOf(ResultHint.NO_RETURN_DATA, ResultHint.CANCELED_NO_DATA)

fun decide(a: PaymentAttemptEntity, r: NormalizedUpiResult, resultCode: Int, p: UpiAppProfile?): Decision {
    val late = a.status == PaymentAttemptStatus.UNRESOLVED && a.resultHint in LATE_OK
    if (a.status != PaymentAttemptStatus.LAUNCHED && !late) return Decision.Ignore

    if (r.txnRef != null && a.sentTxnRef != null && !r.txnRef.equals(a.sentTxnRef, true))
        return Decision.Unresolve(ResultHint.MISMATCH)
    if (r.amountPaise != null && r.amountPaise != a.amountPaise) return Decision.Unresolve(ResultHint.MISMATCH)
    if (r.toVpa != null && !r.toVpa.equals(a.payeeVpa, true)) return Decision.Unresolve(ResultHint.MISMATCH)

    return when (r.outcome) {
        UpiOutcome.FAILURE ->
            if (r.wellFormed) Decision.Fail else Decision.Unresolve(ResultHint.NO_STATUS)
        UpiOutcome.SUCCESS ->
            if (resultCode == Activity.RESULT_OK && r.wellFormed && p?.autoRecordTrusted == true)
                Decision.RecordAuto
            else Decision.Unresolve(ResultHint.APP_SUCCESS_UNVERIFIED)
        UpiOutcome.SUBMITTED -> Decision.Unresolve(ResultHint.APP_SUBMITTED)
        UpiOutcome.NONE ->
            if (resultCode == Activity.RESULT_CANCELED) Decision.Unresolve(ResultHint.CANCELED_NO_DATA)
            else Decision.Unresolve(ResultHint.NO_STATUS)
    }
}
```

An `Unresolve` decision arriving for an attempt that is *already* `UNRESOLVED` (late result) leaves it unchanged.

### 16.5 Decision table

| `resultCode` | Normalized outcome | Checks | App trusted? | Result |
|---|---|---|---|---|
| OK | SUCCESS | pass | yes | **RECORDED** (`APP_REPORTED`) + Undo |
| OK | SUCCESS | pass | no / unknown / chooser launch | UNRESOLVED (`APP_SUCCESS_UNVERIFIED`) |
| any | SUCCESS | mismatch | — | UNRESOLVED (`MISMATCH`) |
| OK or CANCELED | FAILURE | pass | — | **FAILED** |
| any | SUBMITTED | — | — | UNRESOLVED (`APP_SUBMITTED`) |
| OK | none / unrecognized / malformed | — | — | UNRESOLVED (`NO_STATUS`) |
| **CANCELED** | none | — | — | UNRESOLVED (`CANCELED_NO_DATA`) — **never** "cancelled, nothing recorded" |
| no result at all, user returns | — | — | — | UNRESOLVED (`NO_RETURN_DATA`) via reconciler (§19.3) |
| launch exception | — | — | — | FAILED (`LAUNCH_ERROR`) |

### 16.6 Trust policy for `autoRecordTrusted`

A profile may be marked trusted only if Phase 0 shows, for that app and version: status extras are present and parseable on success; **zero false SUCCESS** across ≥10 test payments that include declines, cancels and Back-after-success; and `RESULT_OK` is returned for genuine success. Anything less → `false` (user confirms). Launches through the "Other UPI app" chooser are never trusted (the handling app is unknown).

### 16.7 Undo after auto-record

After an auto-record Pocket shows a snackbar "Recorded ₹450 · Food" with **Undo** (~8 s). Undo deletes the created transaction via the existing delete path. The attempt stays `RECORDED` (terminal) and will not re-record. If Pocket is closed first, the expense remains editable/deletable from History.

---

## 17. Return-to-Pocket UX

### 17.1 When sheets appear

After any decision or reconciliation that produces `FAILED` or `UNRESOLVED`, Pocket pops Screens 1–2 off the back stack, shows Home, and presents the matching bottom sheet **once**. Add `last_prompted_at` to `payment_attempts` (§14.1) so an attempt is never modally prompted twice; afterwards it lives in the Home banner and the Payments-to-Confirm list.

### 17.2 Sheets

**Auto-recorded:** snackbar only (§16.7).

**FAILED**
```text
Payment didn't go through
{Merchant} · ₹450

No expense was recorded.

[ Try another UPI app ]   [ Done ]
```

**UNRESOLVED**, copy varies by hint:

| Hint | Title | Body |
|---|---|---|
| `CANCELED_NO_DATA`, `NO_RETURN_DATA` | Did you complete the payment? | Pocket didn't get a result from {App}. If you paid before closing it, choose Yes. |
| `NO_STATUS` | Couldn't confirm this payment | {App} didn't send a clear result. Check its history if you're unsure. |
| `APP_SUBMITTED` | Your bank is still processing | {App} says the payment was submitted. It may still succeed or fail. |
| `APP_SUCCESS_UNVERIFIED` | {App} reported success | Add ₹450 to {Category}? |
| `MISMATCH` | The result didn't match | The amount or payee returned by {App} differs from this payment. Please check {App}'s history before confirming. |
| `RESTORED` | Restored from backup | This payment was still unconfirmed when the backup was made. |

Actions (same for all):

```text
[ Yes, I paid ₹450 ]        → RECORDED (USER_CONFIRMED)
[ No, it didn't go through ]→ DISCARDED
[ Not sure yet ]            → stays UNRESOLVED   (also: swipe-dismiss)
```

Rules: never title or body text saying "Payment failed" / "Payment cancelled" for an `UNRESOLVED` attempt; no destructive default focus; "Not sure yet" never records or discards.

### 17.3 Home banner

While any attempt is `UNRESOLVED` and younger than 7 days: "1 payment to confirm →" opens the list. After 7 days the banner stops; the attempt remains in the list until it expires at 30 days.

### 17.4 Payments to Confirm screen

Secondary screen reachable from the banner and from Settings.

| Section | Content |
|---|---|
| To confirm | Each `UNRESOLVED` attempt: merchant, amount, time, hint chip, the three actions from §17.2 |
| Expired (30 days) | Read-only; **Add manually** opens the existing Add Expense flow prefilled (merchant, amount, category), recorded as a normal manual expense |

### 17.5 Retry rules

- **Try another UPI app** is offered after `FAILED`, and as a follow-up snackbar action after the user answers **"No, it didn't go through"** on an unresolved sheet (they have explicitly said it did not happen). It creates a **new** attempt (new `attemptId`). It reopens Screen 2 prefilled from the `RetryContext` below with the **payment-app overlay already open**, even if a default app is set (§12.4). Choosing an app there is one-time unless the user ticks "Set as default payment app".
- The data needed for the retry (`ScannedUpiPayment`, chosen category) is handed to the sheet as an **in-memory `RetryContext`**, read from the attempt just before `raw_upi_uri` is cleared. It is dropped when the sheet is dismissed or the process dies; after that the user simply scans again. Raw URIs are therefore never kept on disk after a terminal state.
- "Try again" is **never** offered on an attempt that is still `UNRESOLVED`: it may already have been paid. The user resolves it first, or proceeds through the §9.5 interstitial.

---

## 18. Duplicate and Double-Payment Protection

Three distinct risks, three mechanisms.

### 18.1 Single-flight

At most one attempt may be in `CREATED` or `LAUNCHED`. Room cannot declare partial unique indexes, so enforce inside the creating transaction:

```kotlin
suspend fun createAttempt(a: PaymentAttemptEntity) = db.withTransaction {
    if (attempts.countActive() > 0) throw AttemptInFlight()   // status IN ('CREATED','LAUNCHED')
    attempts.insert(a)
}
```

A stale `LAUNCHED` attempt cannot block forever: the reconciler (§19.3) moves it to `UNRESOLVED`.

### 18.2 UI debounce

The Pay CTA is disabled synchronously on tap, and the overlay rows are disabled synchronously on the first row tap; both stay disabled until the launch outcome is known (or the overlay is dismissed). Rapid double taps produce one attempt; a second `createAttempt` is rejected by §18.1 regardless.

### 18.3 Idempotent finalization (database is the arbiter)

```kotlin
@Query("""UPDATE payment_attempts
          SET status = 'RECORDED', recorded_source = :source,
              resolved_at = :now, updated_at = :now, raw_upi_uri = NULL
          WHERE id = :id AND status IN (:from)""")
suspend fun markRecorded(id: String, source: RecordedSource,
                         from: List<PaymentAttemptStatus>, now: Long): Int

suspend fun recordExpense(id: String, source: RecordedSource,
                          allowedFrom: List<PaymentAttemptStatus>): Boolean =
    db.withTransaction {
        val a = attempts.get(id) ?: return@withTransaction false
        if (attempts.markRecorded(id, source, allowedFrom, clock.now()) != 1) return@withTransaction false
        transactions.insert(a.toExpense(source))                      // ABORT on conflict
        memory.upsert(MerchantCategoryMemory(a.payeeVpaKey, a.categoryId, clock.now()))
        true
    }
```

- The `WHERE status IN (:from)` guard means a repeated callback updates **0 rows** and inserts nothing.
- `UNIQUE(transactions.payment_attempt_id)` is the backstop: a violation aborts the whole transaction, including the status update.
- Equivalent conditional queries exist for `FAILED`, `DISCARDED`, `EXPIRED`, `UNRESOLVED`.

### 18.4 Repeat-payment guards

The §9.5 interstitials cover the user-driven cases (paying the same merchant again while something is unconfirmed, or immediately after a recorded payment). Re-scanning the same QR is allowed; the guards decide whether to warn.

---

## 19. Lifecycle and Process Death

### 19.1 Launch sequence (order matters)

```text
1. Validate amount + category                            (UI)
2. Pre-launch warnings (§9.5)                            (UI)
3. Resolve payment app: default → use it, else overlay   (UI, §12.2)
     └─ overlay dismissed → stop; nothing was created
4. createAttempt(status = CREATED)                       (DB, single-flight check)
5. Build request + intent                                (pure)
6. transition CREATED → LAUNCHED, set launched_at        (DB)
7. launcher.launch(intent)                               (UI)
     └─ on exception → transition to FAILED(LAUNCH_ERROR)
```

The attempt exists on disk **before** the UPI app opens. No attempt exists before step 4, so dismissing the overlay leaves no trace. If Pocket dies between steps 4 and 6, startup cleanup (§15.1 #4) discards the `CREATED` row.

### 19.2 Launcher registration

Register the `ActivityResultLauncher` **in the Activity** (or another always-present lifecycle owner), not conditionally inside a screen composable. Route results to an app-scoped `PaymentResultCoordinator` that works **from database state**, never from ViewModel or screen memory. This is what lets a result arrive correctly after configuration change or process recreation.

### 19.3 Reconciler

Activity Result callbacks are delivered when the lifecycle owner reaches `STARTED`, i.e. **before** `RESUMED` **[VERIFY with an instrumentation test; the reconciler's correctness depends on this ordering]**.

On every `ON_RESUME` of the main Activity:

```text
for each attempt where status = LAUNCHED and (now - launched_at) > 5 s:
    transition LAUNCHED → UNRESOLVED, hint = NO_RETURN_DATA
```

The 5 s guard prevents catching the brief resume that can occur while Pocket is still departing. Because results are dispatched first, a genuine result always wins; the reconciler only fires when **no result was delivered** (returned via Recents/Back-stack, or the UPI app never reported).

On app start (cold): also run startup cleanup (`CREATED` > 60 s → `DISCARDED`), expiry (`UNRESOLVED` > 30 days → `EXPIRED`) and purge (§14.6).

### 19.4 Late results

The user may leave to check something, return to Pocket (reconciler → `UNRESOLVED`), then finish paying, after which the UPI app may still deliver a result. Rows #9/#10 of the transition table allow a **clean** late result to resolve an attempt whose hint is `NO_RETURN_DATA` or `CANCELED_NO_DATA`. Other late outcomes are ignored.

### 19.5 Other cases

| Situation | Behavior |
|---|---|
| Configuration change before Pay | Scanned payment restored from `SavedStateHandle` |
| Configuration change while the overlay is open | Overlay open state and the "Set as default" tick restored from `SavedStateHandle`; no attempt exists yet |
| Configuration change during payment | Nothing in memory matters; DB drives everything |
| Process death during payment | Result (if any) delivered to the recreated Activity; coordinator reads DB. If none, reconciler on next resume |
| User opens Pocket from launcher (new task) while attempt is `LAUNCHED` | Reconciler fires at resume → `UNRESOLVED` |
| Two Pocket tasks | Not supported; `launchMode` of the main Activity stays as-is, but attempts are DB-global so single-flight still holds |
| Device rotated in UPI app | Irrelevant to Pocket |

---

## 20. Architecture and Package Structure

```text
Compose UI ─► ViewModel ─► UseCases ─► Repositories ─► Room
                                  │
                                  └─► upi/ (pure logic + Android launcher)
```

```text
feature/qrpay/
  scanner/
    QrScannerScreen.kt              CameraX preview + overlay
    QrScannerViewModel.kt
    QrAnalyzer.kt                   ML Kit bundled, QR only
  setup/
    PaymentSetupScreen.kt
    PaymentSetupViewModel.kt        holds ScannedUpiPayment in SavedStateHandle
    CategoryGrid.kt
    PaymentAppOverlay.kt            §12.4 (PAY mode) and §12.6 (MANAGE mode)
  confirm/
    PaymentOutcomeSheets.kt         §17.2
    PaymentsToConfirmScreen.kt      §17.4
  domain/
    ParseUpiQrUseCase.kt
    CreatePaymentAttemptUseCase.kt
    ResolvePaymentAppUseCase.kt     §12.2 (pure decision)
    LaunchUpiPaymentUseCase.kt
    ResolveResultUseCase.kt         normalize → decide → persist
    ReconcileAttemptsUseCase.kt     §19.3
    ConfirmAttemptUseCase.kt        user actions §17.2
    RecordExpenseFromAttemptUseCase.kt
  upi/                              (pure Kotlin except where noted)
    UpiQrParser.kt
    UpiRequestBuilder.kt            §13
    UpiResultNormalizer.kt          §16.2
    ResultDecider.kt                §16.4
    UpiAppProfiles.kt               §12.3
    UpiAppDetector.kt               (Android: PackageManager)
    UpiIntentFactory.kt             (Android)
  data/
    PaymentAttemptEntity.kt / Dao.kt
    MerchantCategoryMemory.kt / Dao.kt
    PaymentRepository.kt
    PaymentAppPreferences.kt        default_upi_package, §12.5
  coordinator/
    PaymentResultCoordinator.kt     app-scoped, DB-driven (§19.2)
src/debug/
  UpiSpikeScreen.kt                 Phase 0 harness — debug builds only
```

Keep pure logic (`UpiQrParser`, `UpiRequestBuilder`, `UpiResultNormalizer`, `ResultDecider`, state reducer) free of Android types so it is fast to test on the JVM.

---

## 21. Manifest and Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />

<queries>
    <!-- The three overlay apps (verify package names in Phase 0) -->
    <package android:name="com.google.android.apps.nbu.paisa.user" />
    <package android:name="net.one97.paytm" />
    <package android:name="com.phonepe.app" />

    <!-- Any app that can handle upi://pay (detection for the "Other UPI app" path) -->
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="upi" android:host="pay" />
    </intent>
</queries>
```

- Launching an intent does **not** need visibility; **detecting** installed apps does. The three package entries make "installed?" deterministic for the overlay (§12.7) and are required for Plan B's `getLaunchIntentForPackage`. The intent entry covers the "Other UPI app" path. Do **not** add `QUERY_ALL_PACKAGES`.
- Adding a fourth supported app later means one more `<package>` line here and one more `UpiAppProfile` (§12.3).
- Not requested: `READ_SMS`, `RECEIVE_SMS`, `READ_CONTACTS`, location, storage/media (Photo Picker needs none), `INTERNET`.

---

## 22. Security

### 22.1 Threat model

| Threat | Mitigation |
|---|---|
| Malicious/spoofed QR (sticker over a real QR, misleading `pn`) | Show full VPA; label `pn` unverified (§9.2); the UPI app shows its own payee verification before PIN |
| Oversized or malformed payload | 2048-char cap, strict parser, no regex-catastrophic patterns (§8.3) |
| Injecting arbitrary intent data | Only a string beginning `upi://pay` is ever launched; scheme/authority checked; no extras forwarded |
| URL in `url` param opened automatically | Never opened |
| Sensitive data in logs | Log policy §23.1; release builds strip the URI |
| Raw request left on disk | `raw_upi_uri` nulled at terminal state (§14.6); short-lived |
| Pocket tricked into recording an unpaid expense | Auto-record only for trusted apps with clean, correlated results; else user confirms |
| Result spoofed by another app | Results arrive through the Activity Result channel of the launched Activity; direct launches use `setPackage`. A mismatch or untrusted app never auto-records |
| Double-spend from retries | §18 |

### 22.2 Never

- Store UPI PIN, bank credentials, card data, OTPs.
- Upload QR contents, VPAs, amounts, or transaction state anywhere.
- Log a complete `upi://` URI, VPA or payee name in **any** build channel that leaves the device.
- Treat a merchant display name as proof of payment identity.
- Claim bank-level confirmation of payment.

---

## 23. Privacy, Logging, Backup

### 23.1 Logging

Allowed: `attemptId`, `status`, `provider`, `launchPlan`, `amountPaise`, `resultHint`, normalized status enum, response **format** names, result **code**. Forbidden: URI, VPA, name, note, raw extras, `txnId`, `approvalRef`. Enforce with a lint/detekt rule and a unit test on the logging helper.

### 23.2 On-device data

Everything stays on the device: QR payload, merchant, VPA, amount, category, attempt state. No Pocket backend. The external UPI app is subject to its own privacy model.

### 23.3 Analytics

None. No Firebase Analytics, crash reporting with payload capture, merchant tracking, or remote QR logging.

### 23.4 Backup and restore

- Add `paymentAttempts` and `merchantCategoryMemory` arrays to the backup JSON; bump the backup schema version. Importers MUST tolerate their absence (older backups).
- **Exclude** `raw_upi_uri` from backups.
- On restore: `LAUNCHED` and `CREATED` → `UNRESOLVED` with hint `RESTORED` only if younger than 30 days, otherwise `EXPIRED`; already-expired attempts stay expired. This prevents a restore from resurrecting stale "did you pay?" prompts.
- Backups now contain VPAs of merchants. Surface this in the existing backup privacy copy.
- The **default payment app** preference is device-specific and is **not** backed up or restored.

---

## 24. Room Migration

Schema version `N → N+1` (numbers are placeholders). Never use destructive migration. Existing transactions must survive unchanged.

```sql
CREATE TABLE IF NOT EXISTS payment_attempts (
    id TEXT NOT NULL PRIMARY KEY,
    payee_vpa TEXT NOT NULL,
    payee_vpa_key TEXT NOT NULL,
    payee_name TEXT,
    merchant_category_code TEXT,
    amount_paise INTEGER NOT NULL,
    currency TEXT NOT NULL,
    category_id INTEGER NOT NULL,
    local_note TEXT,
    launch_plan TEXT NOT NULL,
    sent_txn_ref TEXT,
    qr_kind TEXT NOT NULL,
    raw_upi_uri TEXT,
    provider TEXT NOT NULL,
    provider_package TEXT,
    status TEXT NOT NULL,
    result_hint TEXT NOT NULL,
    result_code INTEGER,
    app_status TEXT,
    app_txn_id TEXT,
    app_approval_ref TEXT,
    app_response_code TEXT,
    recorded_source TEXT,
    created_at INTEGER NOT NULL,
    launched_at INTEGER,
    resolved_at INTEGER,
    last_prompted_at INTEGER,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS index_payment_attempts_status ON payment_attempts(status);
CREATE INDEX IF NOT EXISTS index_payment_attempts_payee_vpa_key ON payment_attempts(payee_vpa_key);
CREATE INDEX IF NOT EXISTS index_payment_attempts_created_at ON payment_attempts(created_at);

CREATE TABLE IF NOT EXISTS merchant_category_memory (
    payee_vpa_key TEXT NOT NULL PRIMARY KEY,
    category_id INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

ALTER TABLE transactions ADD COLUMN payment_attempt_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_payment_attempt_id
    ON transactions(payment_attempt_id);
```

The column and index definitions MUST match the Room entities exactly, or Room's schema validation fails at runtime. Export schemas (`room.schemaLocation`) and verify with `MigrationTestHelper` (§27.3). Adapt column types (e.g. timestamps) to Pocket's existing convention.

---

## 25. Accessibility, Localization, Performance

### 25.1 Accessibility

- Every interactive element has a content description built from **state**, not hard-coded examples:
  `"Food category, selected"`, `"Groceries category, not selected"`, `"Pay securely, 450 rupees"`, `"Scan QR code"`, `"Turn flashlight on"`.
- Minimum 48 dp touch targets; logical focus order (merchant → amount → categories → note → Pay → change app).
- Selection is never color-only (check icon, border, semantics `selected`).
- Scanner: announce "UPI QR code detected" through a live region on success; the gallery button is reachable without seeing the camera.
- Sheets: focus moves to the title on open; the three actions are separate, labelled buttons.
- Payment-app overlay: each row is a button whose description includes its state (`"Google Pay, default"`, `"Paytm, not installed, unavailable"`); the checkbox has a label and a checked state; rows and checkbox are reachable in order; Back and scrim dismiss it; the MANAGE-mode radio group exposes the selected option.
- Layout survives 200% font scale (screen scrolls, CTA stays pinned). Dark mode supported; scanner overlay meets contrast in both.

### 25.2 Localization

All strings in resources (no literals in Composables). Currency and numbers use an explicit `Locale("en","IN")` for display (§10). Banner text uses plurals. Layouts avoid fixed widths so longer translations do not clip.

### 25.3 Performance targets (SHOULD, measure on a mid-range device)

- Camera preview visible in ≈1 s from tapping Scan.
- Screen 2 appears within ≈300 ms of a successful decode.
- No database or parsing work on the main thread; analyzer on a background executor with `KEEP_ONLY_LATEST`.
- Camera released when leaving the scanner.
- Measure the APK size impact of the bundled ML Kit model and record it.

---

## 26. Feature Flag and Rollout

- Ship behind **Settings → Experimental → "QR Pay (beta)"**, default **off** in the first release.
- Turning it off hides the Home Scan action **but never hides unresolved attempts**: the Home banner and Payments-to-Confirm screen stay reachable so nobody loses a payment they still need to confirm.
- Ship first to a small group of real users; the review questions are the Phase 0 ones: which apps block, which return clean results, how often does the confirmation sheet appear.
- Update `UpiAppProfile` in point releases as observed behavior changes (§12.3).

---

## 27. Testing Strategy

### 27.1 Unit tests (JVM, pure Kotlin)

**Parser** (`UpiQrParser`) — each is a case:

| Case | Expected |
|---|---|
| Valid merchant QR with amount | `Valid`, `qrAmountPaise` set, `kind = MERCHANT_DYNAMIC` if `sign`/`am` |
| Personal static QR, no `mc`, no `am` | `Valid`, `kind = PERSONAL`, amount `null` |
| Missing `pa` | `Invalid(BAD_VPA)` |
| `pa` with spaces / no `@` / very long | `Invalid(BAD_VPA)` |
| `am=` empty, `am=0`, `am=0.00` | Valid, amount `null` |
| `am=-5`, `am=abc`, `am=1e3`, `am=1.234`, `am=1,000` | `Invalid(BAD_AMOUNT)` |
| `am=450.00`, `am=450`, `am=0.01` | Valid |
| `cu=USD` | `Invalid(NON_INR)`; `cu=inr` valid |
| Duplicate `pa` or `am` | `Invalid(DUPLICATE_PARAM)` |
| Bad `%` escape | `Invalid(MALFORMED)` |
| Encoded name/note (`%20`, `+`, non-ASCII, emoji) | Decoded correctly |
| Unknown params + `sign`, `mode`, `orgid`, `purpose` | Valid; `rawUri` unchanged |
| Upper-case scheme `UPI://PAY?...` | Valid |
| Payload > 2048 chars | `Invalid(TOO_LONG)` |
| `https://…`, plain text, `upi://mandate?…` | `NotUpi` |

**Builder** (`UpiRequestBuilder`):

- Pass-through output is **byte-identical** to `rawUri`.
- Rebuild forwards every non-dropped parameter with identical encoding; removes `am`/`sign`/old `cu`; appends `am` and `cu=INR`.
- Generated `tr` only when absent; ≤35 chars, alphanumeric.
- Optional params absent → parameter omitted (assert no `=null`, no empty `key=`).
- `paiseToUpiAmount` under default locales `en-IN`, `ar`, `de`, `hi-IN` returns the same string.
- `mam` enforced on entered amounts.

**Normalizer** (`UpiResultNormalizer`) fixtures:

| Fixture | Expected |
|---|---|
| Flat extras, `Status=SUCCESS` | `SUCCESS`, `FLAT_EXTRAS` |
| `Status=success`, `Success`, `SUCCESS ` | `SUCCESS` |
| `response=txnId=A&responseCode=00&Status=SUCCESS&txnRef=R` | `SUCCESS`, `RESPONSE_STRING`, ref `R` |
| `tezResponse` JSON with amount, toVpa | `SUCCESS`, amount/VPA parsed |
| `tezResponse` invalid JSON | `wellFormed = false` |
| Two sources with different `Status` | `wellFormed = false` |
| `Status=SUBMITTED` / `PENDING` | `SUBMITTED` |
| `Status=FAILURE` / `FAILED` | `FAILURE` |
| Unknown status / empty map | `NONE` |
| Amount `450.001` in result | `wellFormed = false` |

**Decider** (`decide`): one test per row of the §16.5 table, plus: mismatch ref / amount / VPA; missing `txnRef` (must not mismatch); late result accepted only for `NO_RETURN_DATA`/`CANCELED_NO_DATA`; terminal attempt → `Ignore`; chooser launch (no profile) never `RecordAuto`; untrusted profile never `RecordAuto`.

**State reducer**: every legal transition of §15.1 succeeds; **every other (state, event) pair is rejected** (table-driven over the full cross product).

**Payment-app resolution** (`ResolvePaymentAppUseCase`): one test per row of the §12.2 table, plus:

| Case | Expected |
|---|---|
| No default, three apps installed | `ShowOverlay` (every call, never remembers within a session) |
| Default = Google Pay, installed, not blocked | `Launch(GooglePay)`, **no overlay** |
| Default = Paytm, uninstalled | `ClearDefault` + `ShowOverlay(notice)` |
| Default = PhonePe, `blocked = true` | `ClearDefault` + `ShowOverlay(notice)` |
| Stored value `"com.evil.app"` | Treated as unset and cleared |
| Overlay: row tap with checkbox ticked | Default persisted **then** launch |
| Overlay: row tap with checkbox unticked, default already set | Default unchanged |
| Overlay: "Other UPI app" with checkbox ticked | Default unchanged; provider = `CHOOSER` |
| MANAGE mode: select app / "Ask me every time" | Preference set / cleared; **no attempt created** |

**Logging**: the log helper rejects/redacts strings starting with `upi://` and anything shaped like a VPA.

### 27.2 Database tests (Room, in-memory, instrumented or Robolectric)

- Two concurrent `recordExpense` calls for one attempt → exactly **one** transaction row.
- Second `recordExpense` after the first → returns `false`, no new row.
- `UNIQUE(payment_attempt_id)` violation rolls back the status change too.
- `createAttempt` while one is `CREATED`/`LAUNCHED` → `AttemptInFlight`.
- Startup cleanup, 30-day expiry, 90-day purge, `raw_upi_uri` nulling at every terminal transition.
- Category delete blocked while referenced by an `UNRESOLVED` attempt.
- Merchant memory written only on `RECORDED`.

### 27.3 Migration tests

`MigrationTestHelper`: create schema `N` with sample transactions, migrate to `N+1`, assert all old rows intact, new tables/indexes exist, `payment_attempt_id` is `NULL` for old rows, and Room's schema validation passes.

### 27.4 Compose UI tests

- Payment Setup: amount locked vs editable; `mam` error; CTA disabled until category chosen; pre-selection from memory and from MCC hint with the right captions; CTA disabled synchronously on tap.
- Selected category exposes `selected` semantics; all controls have descriptions.
- Layouts at font scale 1.0, 1.3, 2.0; light and dark.
- Each outcome sheet: correct copy per hint; "Not sure yet" and swipe-dismiss leave the attempt `UNRESOLVED`; no sheet ever contains "failed"/"cancelled" for `UNRESOLVED`.
- Payment-app overlay: three rows in fixed order; row states (available / not installed / unavailable / default badge); checkbox unticked on every open; dismiss by drag, scrim and Back creates no attempt; rows disable on first tap; state survives rotation.
- With a default set: tapping **Pay Securely** shows **no overlay**; the "Paying with {App} · Change" caption is visible; "Change" opens MANAGE mode. With no default: no caption; overlay every time.

### 27.5 Instrumented tests with a Fake UPI app

Build a small test APK (`androidTest` or a separate `fakeupi` module) that declares an `ACTION_VIEW` filter for `upi://pay` and behaves according to a scripted "personality" so the full launch → return → resolve path runs in CI **without real money**:

| Personality | Behavior |
|---|---|
| `SUCCESS_FLAT` / `SUCCESS_RESPONSE` / `SUCCESS_TEZ` | `RESULT_OK` with the respective format |
| `FAILURE` | `RESULT_OK`, `Status=FAILURE` |
| `SUBMITTED` | `RESULT_OK`, `Status=SUBMITTED` |
| `CANCEL_NO_DATA` | `RESULT_CANCELED`, no extras |
| `SUCCESS_THEN_BACK` | Shows success, returns `RESULT_CANCELED` (simulates C5) |
| `NO_RETURN` | Never returns a result; test navigates back to Pocket |
| `MALFORMED` | `RESULT_OK`, garbage extras |
| `MISMATCH_AMOUNT` / `MISMATCH_REF` / `MISMATCH_VPA` | Success with wrong echoed fields |
| `DUPLICATE` | Delivers the result twice |
| `LATE` | Returns after Pocket has already reconciled to `UNRESOLVED` |
| `SLOW` | Returns after a long delay |

Assertions include: one expense for `SUCCESS_*` from a trusted profile; none for everything else until the user confirms; `SUCCESS_THEN_BACK` never shows "cancelled/failed"; `DUPLICATE` creates one expense; chooser launch never auto-records.

Also assert the **ordering assumption** in §19.3 (result callback before reconciler on resume).

The supported-app list is **injected** in tests so the overlay's rows can point at the Fake UPI app's package (a test cannot install Google Pay, Paytm or PhonePe on a CI emulator).

### 27.6 Real-device matrix

Run the Phase 0 matrix (§5.3) again against the release candidate. Add: each of the three apps as default (direct launch, no overlay); the overlay with no default; the "Set as default" tick; Change / "Ask me every time"; default app uninstalled between payments; "Other UPI app"; QR from gallery; camera permission denied/permanent; airplane mode during scanning (must still work) and during payment.

### 27.7 Lifecycle and exploratory tests

```bash
# Kill Pocket while it is in the background (only works for background apps)
adb shell am kill com.your.pocket.package

# Destroy Activities as soon as they leave the foreground
adb shell settings put global always_finish_activities 1
# (restore afterwards)
adb shell settings put global always_finish_activities 0
```

Scenarios: kill Pocket during payment then return through the UPI app's Back; open Pocket from launcher mid-payment; rotate on Screen 2; low-memory kill on an aggressive-memory OEM; two rapid Pay taps; scanning the same QR twice.

---

## 28. Edge-Case Catalog

| # | Situation | Expected behavior |
|---|---|---|
| 1 | User completes payment, presses Back in the UPI app (`RESULT_CANCELED`, no data) | `UNRESOLVED (CANCELED_NO_DATA)` → "Did you complete the payment?" — never "cancelled" |
| 2 | User completes payment, swipes the UPI app away from Recents | Reconciler → `UNRESOLVED (NO_RETURN_DATA)` on next Pocket resume |
| 3 | User genuinely cancels before PIN | Same sheet; user taps "No" → `DISCARDED` |
| 4 | UPI app shows a "risky / can't process" error | Usually no result → same as #3; user taps "No" and gets the **Try another UPI app** action (§17.5) |
| 5 | Default app is uninstalled or becomes blocked | Default cleared; the overlay opens with the notice "{App} isn't available. Choose another app." (§12.5) |
| 6 | UPI app returns SUCCESS but is untrusted | `UNRESOLVED (APP_SUCCESS_UNVERIFIED)` → one-tap confirm |
| 7 | Result echoes a different amount | `UNRESOLVED (MISMATCH)`; warning copy; never auto-records |
| 8 | Same callback delivered twice | Second is a no-op (terminal state / 0 rows updated) |
| 9 | Pocket killed while paying, user returns via UPI Back | Result delivered to recreated Activity; DB-driven resolution |
| 10 | User taps Pay twice quickly | One attempt (UI debounce + single-flight) |
| 11 | User scans the same QR again after paying | Allowed; §9.5 warns if recent/unresolved |
| 12 | QR has amount and `sign` | Locked amount; pass-through; no edits |
| 13 | Static merchant QR, user enters amount | Rebuilt request; `sign` dropped if present; per-app behavior from Phase 0 |
| 14 | QR has `am=0.00` | Treated as absent |
| 15 | QR `cu=USD` | Rejected |
| 16 | Non-UPI QR (URL, text, other UPI intent) | Hint only; keep scanning |
| 17 | No camera / permission permanently denied | Gallery scanning path |
| 18 | No UPI app installed | Dialog: install a UPI app |
| 19 | UPI apps installed but all `blocked` | Same as 18 with "no supported app" copy; Plan B offered if Phase 0 chose it |
| 20 | Category deleted after attempt created | Blocked while `UNRESOLVED`; if already terminal, expense recorded with fallback `other` |
| 21 | Backup restored with unresolved attempts | `RESTORED` hint if < 30 days, else `EXPIRED` |
| 22 | Late clean result after user already confirmed "No" | Terminal → ignored |
| 23 | QR Pay flag turned off with unresolved attempts | Banner and list still shown |
| 24 | Device clock changes | Wall-clock timestamps drive only coarse expiry and the 5 s reconciler guard. The reconciler treats a negative elapsed time as elapsed. A wrong clock can at worst expire an attempt early or late; it can never cause an expense to be recorded |
| 25 | User dismisses the overlay (drag, scrim, Back) | No attempt is created; back on Screen 2 with the CTA enabled |
| 26 | User ticks "Set as default" and the direct launch then throws `ActivityNotFoundException` | Default cleared (§12.5); failure sheet with **Try another UPI app** |
| 27 | Default is set and the user wants a different app | **Change** → MANAGE mode (updates the default). One-time use is available via **Try another UPI app** after a failure |
| 28 | None of Google Pay, Paytm, PhonePe is usable | Overlay opens with all rows disabled; "Other UPI app" is the only action; if none, the `no_upi_app` dialog (§12.7) |
| 29 | Stored default is a stale or unknown package value | Treated as unset and cleared |
| 30 | Two rapid taps on app rows | First tap disables the rows; exactly one attempt |
| 31 | Rotation or backgrounding while the overlay is open | Overlay and tick restored; no attempt until a row is tapped |
| 32 | User ticks "Set as default" then taps "Other UPI app" | Tick ignored; default unchanged; provider = `CHOOSER` |

---

## 29. Release Gate and Compliance

Complete before any public release. This section is a checklist, not legal advice; obtain review where marked.

- [ ] Phase 0 decision (GO / GO-RESTRICTED / PLAN B) recorded (Appendix A).
- [ ] NPCI UPI Linking Specification version and retrieval date recorded (Appendix E); parameter rules in §8 re-checked against it.
- [ ] Google Pay / other provider docs re-read for changes since Phase 0.
- [ ] `UpiAppProfile` list reflects the **latest** measured behavior (blocked / trusted flags, min tested versions).
- [ ] Copy reviewed: the CTA label **Pay Securely** is acceptable only because the overlay states that the payment is completed in the user's UPI app. Nothing may imply that Pocket is a payment app, a bank, or a guarantor of the payment's security.
- [ ] Third-party names appear as text plus the app's own icon from `PackageManager`; no bundled brand logos; provider brand guidelines checked **[VERIFY]**.
- [ ] If distributed through Google Play: Play Console declarations (financial features / data safety) are accurate for an app that launches UPI payments, uses the camera, and stores payee identifiers locally **[VERIFY current policy]**. If distributed as a sideloaded APK, note the reduced but non-zero policy surface.
- [ ] Regulatory positioning reviewed (Pocket initiates via the user's UPI app and does not process payments) — **recommend legal review** before public distribution.
- [ ] Permissions diff vs. previous release: only `CAMERA` added; no `INTERNET`.
- [ ] No analytics/telemetry SDK added.
- [ ] Release build strips debug spike harness; logging rules enforced.
- [ ] Signed with the existing Pocket release key.
- [ ] Feature flag default off (§26).
- [ ] Rollback plan: disabling the flag leaves data and unresolved attempts intact.

---

## 30. Implementation Phases and Exit Criteria

| Phase | Scope | Exit criteria |
|---|---|---|
| **0** | Spike harness, device matrix (§5) | Appendix D complete; GO / GO-RESTRICTED / PLAN B decided; profiles and parsing table finalized |
| **1** | Data layer: entities, DAOs, migration, state reducer, atomic finalization, retention | §27.1 reducer + §27.2 + §27.3 tests green |
| **2** | QR parser + scanner (CameraX + ML Kit bundled, gallery, permission states) | Parser table green; scanner works offline on ≥3 devices |
| **3** | Payment Setup UI: locked/editable amount, six categories, memory & MCC pre-selection, warnings, **payment-app overlay (PAY and MANAGE modes)**, dark mode, a11y | §27.4 green |
| **4** | App detection, default-app preference, `ResolvePaymentAppUseCase`, "Other UPI app" chooser, request builder, launch sequence | Resolution table (§12.2) and builder tests green; Fake UPI launches work for direct + chooser; default-set path skips the overlay |
| **5** | Result normalizer, decider, coordinator, Undo | Normalizer/decider tables green; Fake UPI personalities all pass |
| **6** | Reconciler, confirmation sheets, Home banner, Payments-to-Confirm, backup/restore | Lifecycle tests (§27.7) pass; restore behavior verified |
| **7** | Hardening: real-device matrix on RC, performance, a11y audit | §27.6 done; no P0/P1 open |
| **8** | Release gate (§29) | Checklist complete |

Do not start Phase 1 before Phase 0 exits. If Phase 0 selects **Plan B**, Phases 1–3 and 5–8 proceed unchanged; Phase 4 replaces the request builder/intent with "open the UPI app" (§5.5).

---

## 31. Definition of Done

### UX
- [ ] Home has a Scan action; QR Pay is not a bottom-nav destination.
- [ ] Exactly two primary Pocket screens before handoff; app choice is an overlay (bottom sheet), not a screen.
- [ ] Amount is locked when QR-supplied; editable and validated (`mam`) otherwise.
- [ ] Six categories visible without an extra page; pre-selection from memory/MCC works and is labelled.
- [ ] The CTA reads **Pay Securely** and never names an app.
- [ ] No default set: tapping it opens the overlay listing **Google Pay, Paytm, PhonePe** — **every time**.
- [ ] "Set as default payment app" (unticked on every open) persists the chosen app.
- [ ] Default set: tapping it launches that app **directly, with no overlay**, and "Paying with {App} · Change" is shown.
- [ ] **Change** and Settings → Default payment app can switch the default or return to "Ask me every time" without starting a payment.
- [ ] An unusable default (uninstalled/blocked) is cleared and the overlay opens with a notice.
- [ ] "Other UPI app" remains reachable and never auto-records.
- [ ] Merchant name is labelled unverified; VPA is fully visible.

### QR
- [ ] Every row of the §27.1 parser table passes.
- [ ] Works fully offline (airplane mode) including gallery scan.
- [ ] Non-UPI and invalid QRs are handled without navigation.

### Payment
- [ ] `PaymentAttempt` is persisted before any UPI app launches.
- [ ] Amount-bearing QRs are launched as byte-identical pass-through.
- [ ] Amount-less QRs are rebuilt per §13.2 with no `null`/empty parameters.
- [ ] Launch works through direct package **and** system chooser on real devices.
- [ ] A ₹1 payment completes end-to-end and is recorded correctly on **Google Pay, Paytm and PhonePe** (or the Phase 0 GO-RESTRICTED subset), on ≥2 devices.
- [ ] Pocket never handles PIN or credentials.

### Result
- [ ] Clean trusted SUCCESS records exactly one expense with Undo.
- [ ] FAILURE records nothing.
- [ ] SUBMITTED / cancelled-no-data / no-return / malformed / mismatch → `UNRESOLVED` and never claim failure.
- [ ] `SUCCESS_THEN_BACK` scenario verified on real apps.
- [ ] Duplicate callbacks and concurrent finalization cannot duplicate expenses.
- [ ] Late results resolve only when permitted (§19.4).
- [ ] Chooser launches and untrusted apps never auto-record.

### Lifecycle
- [ ] Rotation, process death, Recents-swipe, launcher re-entry and "don't keep activities" all leave consistent state.
- [ ] Reconciler runs after result delivery (ordering test passes).
- [ ] Unresolved attempts survive restart and are visible via banner + list.

### Data
- [ ] Migration test passes; old data intact; schema matches Room.
- [ ] `raw_upi_uri` cleared on every terminal state.
- [ ] Backup includes attempts and memory (not raw URIs); restore rules verified.
- [ ] Retention/expiry/purge verified.

### Security and privacy
- [ ] No PIN, credentials or full URIs stored or logged.
- [ ] No analytics, no `INTERNET`, no extra permissions.
- [ ] Log redaction test passes.

### Accessibility and quality
- [ ] TalkBack pass on both screens and all sheets; 200% font scale; dark mode.
- [ ] All unit, DB, migration, UI and Fake-UPI tests green in CI.

### Release
- [ ] §29 checklist complete; feature flag defaults off.

---

## 32. Final Product Contract

```text
                   POCKET
                     │
              Scan UPI QR (local)
                     │
              Parse + validate
                     │
        ┌────────────▼────────────┐
        │     Payment Setup       │
        │ merchant · amount 🔒/✎  │
        │ category (remembered)   │
        │ [ Pay Securely ]        │
        └────────────┬────────────┘
                     │
        default app set? ── yes ──────────┐
                     │ no                 │
                     ▼                    │
        ┌────────────────────────┐        │
        │ Overlay: Google Pay ·  │        │
        │ Paytm · PhonePe        │        │
        │ [ ] Set as default     │        │
        └────────────┬───────────┘        │
                     └──────────┬─────────┘
                                │ persist attempt → launch
                                ▼
      UPI app (native UI · PIN · bank)
                     │
         ┌───────────┼─────────────────┐
         ▼           ▼                 ▼
   clean, trusted  explicit        anything else
      SUCCESS      FAILURE      (cancel/no data/submitted/
         │           │            mismatch/no return)
         ▼           ▼                 ▼
   record + Undo  nothing      "Did this payment go through?"
                                 Yes → record · No → discard
                                 Not sure → stays pending
```

> **Pocket owns the experience before payment. The UPI app owns the payment. Pocket records an expense only as far as the evidence allows — and asks the user when the evidence isn't enough.**

---

## Appendix A — Decision Log and Change History

### 2.1 — Payment-app overlay and default app

| # | Change | § |
|---|---|---|
| 1 | CTA is now **Pay Securely** and no longer names an app | 9 |
| 2 | Replaced "Google Pay-first / remembered on first use" with an explicit **default payment app** preference; none is set out of the box | 12.1, 12.5 |
| 3 | Overlay lists **Google Pay, Paytm, PhonePe** (fixed order), plus a small "Other UPI app" link | 12.3, 12.4 |
| 4 | **Set as default payment app** checkbox (unticked on every open) | 12.4 |
| 5 | Default set → **direct launch, no overlay**; caption "Paying with {App} · Change" | 12.2, 12.5 |
| 6 | No default → overlay **every** payment | 12.1 |
| 7 | MANAGE mode (Change link, Settings) to switch default or "Ask me every time"; never starts a payment | 12.6 |
| 8 | Unusable default (uninstalled/blocked) auto-cleared with a notice | 12.2, 12.5 |
| 9 | Launch sequence gained a payment-app resolution step; overlay dismissal leaves no attempt | 19.1 |
| 10 | Package `<queries>` entries for the three apps (also needed by Plan B) | 21 |
| 11 | BHIM removed from the required Phase 0 set (still reachable via "Other UPI app") | 5.3 |
| 12 | Tests, edge cases, copy deck, DoD, phases updated | 27, 28, 31, 30, App. B |

### 2.0 — changes from v1

| # | v1 issue | v2 resolution | § |
|---|---|---|---|
| 1 | Feasibility gate was the last phase; intent-blocking risk never mentioned | Phase 0 spike as a hard gate, with GO / GO-RESTRICTED / PLAN B | 5 |
| 2 | Merchant docs used as authority for non-merchant use | NPCI spec + Phase 0 measurements are the sources of truth | 4 |
| 3 | Rebuilt URI dropped `sign`/`mode`/`orgid`; "unknown params ignorable" | Pass-through for amount QRs; byte-preserving rebuild otherwise | 8, 13 |
| 4 | User could edit QR-supplied amount | Amount locked when QR supplies it | 9.3 |
| 5 | Overrode QR `tr` with Pocket's | `attemptId` is Pocket's key; `tr` only generated when absent | 13.3 |
| 6 | Cancelled result → "no expense was recorded" + Try again | Cancelled-with-no-data → `UNRESOLVED`; no Try again on unresolved | 16, 17 |
| 7 | No handling of returning without a result | `ON_RESUME` reconciler, late-result rule | 19 |
| 8 | Fixtures covered one response shape only | Three-format normalizer, casing/conflict rules, never trust `responseCode` | 16.2 |
| 9 | Validation assumed amount/VPA always returned | Validate only when present; absence is normal | 16.3 |
| 10 | Google Pay hard-coded; generic path only if missing | Remembered app + always-available generic path (superseded in 2.1 by the overlay + explicit default app; the generic path stays as "Other UPI app") | 12 |
| 11 | Package visibility for Google Pay only | Intent-based `<queries>` | 21 |
| 12 | Category inference forbidden | Merchant memory + optional MCC hint | 11.3 |
| 13 | State machine and enum disagreed; UNKNOWN dead end | One enum, hints, full transition table, all states resolvable | 14, 15 |
| 14 | Stale pending attempts / backup resurrection | Retention, expiry, restore rules | 14.6, 23.4 |
| 15 | Duplicate protection only for callbacks | Single-flight, debounce, conditional updates, unique index, warnings | 18, 9.5 |
| 16 | Non-atomic finalization sample | `UPDATE … WHERE status IN (…)` + unique backstop | 18.3 |
| 17 | Sample code could emit `"null"`; locale-dependent amount | Omit absent params; `BigDecimal.toPlainString` + locale test | 10, 13 |
| 18 | Provider enum could not support §21 mapping | `DIRECT_APP` / `CHOOSER` with `providerPackage` | 14 |
| 19 | Scanner tech unspecified | CameraX + ML Kit bundled, Photo Picker, camera not required | 7.3 |
| 20 | Thin QR safety | VPA validation, size cap, unverified-name caption | 8.3, 22 |
| 21 | Missing screens (result, pending list, entry) | Sheets, Home banner, Payments-to-Confirm, Home top-bar entry | 6, 17 |
| 22 | Overlapping "source" values | `QR_APP_REPORTED`, `QR_USER_CONFIRMED` | 14.4 |
| 23 | DoD checked "GPay opens" | DoD requires real end-to-end ₹1 payments on ≥3 apps | 31 |
| 24 | Test matrix lacked app × QR × device; no CI-safe payment tests | Fake UPI app, real matrix, adb scripts | 27 |

### Phase 0 decision record (fill in)

| Field | Value |
|---|---|
| Date | |
| Devices / OS | |
| Apps + versions | |
| Decision | GO / GO-RESTRICTED / PLAN B |
| Trusted apps | |
| Blocked apps | |
| Notes | |

---

## Appendix B — Copy Deck (V1, English)

| Key | Text |
|---|---|
| scanner_title | Scan merchant QR |
| scanner_hint | Point your camera at a UPI QR code |
| scanner_not_upi | This isn't a UPI payment QR |
| scanner_bad_vpa | This QR has an invalid UPI ID |
| scanner_non_inr | Pocket only supports INR payments |
| camera_denied | Camera access is needed to scan a UPI QR |
| camera_error | Pocket couldn't access the camera |
| gallery_action | Choose from gallery |
| setup_paying | Paying |
| setup_name_unverified | Name comes from the QR and isn't verified |
| setup_amount_locked | Set by the merchant |
| setup_min_amount | Minimum amount is {amount} |
| setup_choose_category | Choose a category |
| setup_last_used | Last used |
| setup_suggested | Suggested |
| setup_add_note | Add a note |
| cta_pay | Pay Securely |
| default_caption | Paying with {app} · Change |
| overlay_title | Pay {amount} to {merchant} |
| overlay_subtitle | You'll complete the payment in your UPI app. |
| overlay_set_default | Set as default payment app |
| overlay_other_app | Other UPI app |
| overlay_default_badge | Default |
| overlay_not_installed | Not installed |
| overlay_unavailable | Can't be used with Pocket right now |
| overlay_default_unavailable | {app} isn't available. Choose another app. |
| manage_title | Default payment app |
| manage_ask_every_time | Ask me every time |
| manage_saved | {app} is now your default |
| manage_cleared | Pocket will ask each time |
| no_upi_app | No UPI app found. Install one and try again. |
| no_supported_upi_app | None of your UPI apps can be used with Pocket right now. |
| launch_error | Pocket couldn't open {app}. Try another UPI app. |
| warn_unresolved_title | You have an unconfirmed payment |
| warn_unresolved_body | Confirm your earlier payment to {merchant} first to avoid paying twice. |
| warn_recent_title | You just paid this merchant |
| warn_recent_body | You paid {amount} to {merchant} moments ago. |
| warn_large_body | UPI limits differ by bank and app; your UPI app will enforce them. |
| recorded_snackbar | Recorded {amount} · {category} |
| undo | Undo |
| failed_title | Payment didn't go through |
| failed_body | No expense was recorded. |
| unresolved_yes | Yes, I paid {amount} |
| unresolved_no | No, it didn't go through |
| unresolved_later | Not sure yet |
| banner_pending | {n, plural, one {# payment to confirm} other {# payments to confirm}} |

Unresolved-sheet titles/bodies: §17.2.

---

## Appendix C — Open Items Requiring External Verification

| # | Item | Resolved by |
|---|---|---|
| 1 | Whether each target UPI app accepts intent payments from a non-merchant app, for each QR type | Phase 0 |
| 2 | Whether dropping `sign` on a rebuilt static-merchant request is accepted per app | Phase 0 |
| 3 | Per-app result formats, status casing, `txnRef` echo, cancel/back semantics | Phase 0 |
| 4 | Whether Activity Result is delivered before `ON_RESUME` on all supported OS versions | §27.5 ordering test |
| 5 | Current NPCI limits on `tr` / `tn` length and any new parameters | NPCI Linking Spec at release time |
| 6 | Provider brand-guideline wording for "Pay with {app}" | Provider guidelines |
| 7 | Play Console policy/declaration requirements (if distributed via Play) | Current Play policy |
| 8 | Regulatory positioning of an app that initiates UPI intents | Legal review |
| 9 | Whether Google Pay's success return remains reliable across versions | Re-run Phase 0 per release |
| 10 | Package names of Google Pay, Paytm and PhonePe, and that each exposes a `upi://pay` handler | Phase 0 (on-device) |

---

## Appendix D — Phase 0 Results Template

One row per (app, version, device/OS, QR type, launch path):

| App / ver | Device / OS | QR type | Launch | Accepted? | Blocked msg | Prefill OK | resultCode | Extra keys | Status (raw) | txnRef echoed | Amount/VPA echoed | Returns to Pocket on its own | Result on Back-after-success | Result on cancel | Trusted? |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | | | | | | |

---

## Appendix E — References

Re-read at implementation time and again before release; record the date reviewed.

| Topic | Reference | Reviewed |
|---|---|---|
| Google Pay for India — Android overview (prerequisites, unique transaction IDs, Android 11 `<queries>`) | https://developers.google.com/pay-wallet/regions/india/api/android/overview | |
| Google Pay for India — Android in-app payments (`upi://pay`, package name, `startActivityForResult`, generic-intent note) | https://developers.google.com/pay-wallet/regions/india/api/android/in-app-payments | |
| Google Pay response fields (`Status`, `txnRef`, `tezResponse`) | https://developers.google.com/pay-wallet/regions/india/api/web/googlepay-response | |
| Google Pay transaction details (merchant-side verification) | https://developers.google.com/pay-wallet/regions/india/api/otherapis/omnichannel/get-transaction-details | |
| NPCI UPI Linking Specification (request/response parameters, deep-link and QR formats) | Obtain the **current** version from NPCI's UPI developer resources; record version: ______ date: ______ | |
| Android Activity Result APIs, package visibility, Photo Picker, CameraX, ML Kit Barcode Scanning | Official Android / Google developer documentation (current) | |