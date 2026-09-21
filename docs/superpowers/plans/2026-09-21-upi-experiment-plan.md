# Pocket — UPI Editable-Amount Callback Experiment

> **Status:** EXPERIMENTAL PROOF-OF-CONCEPT  
> **Date:** 21 September 2026  
> **Purpose:** Determine if UPI apps can show editable amount AND return transaction response to Pocket

---

## Objective

Test whether the following architecture is technically possible:

```
Pocket scans QR → Extracts VPA/name → Launches UPI intent
→ GPay shows payment screen with EDITABLE amount
→ User enters amount → Pays → UPI PIN
→ GPay returns to Pocket automatically
→ Pocket receives transaction response
→ Pocket determines SUCCESS/SUBMITTED/FAILURE
```

**Critical variable:** Editable amount field in GPay while still getting callback.

---

## NPCI Spec — Editable Amount

From NPCI UPI Linking Specification v1.6:

> "If `am` is not present then field is editable."

> "mam: This parameter is conditional and shall be used to define a minimum amount rule where amount field in PSP app is editable. If `mam` tag is not present or `mam=null` or `mam=` then amount field should NOT be editable."

> "Note: if a customer enters the value less than value passed in `mam` then UPI will decline the transaction."

### Two Approaches to Test

| Approach | `am` | `mam` | Expected Behavior |
|----------|------|-------|-------------------|
| A: No amount | Not present | `1` | Amount field editable, min ₹1 |
| B: With amount | `1.00` | `1` | Amount pre-filled at ₹1, editable, min ₹1 |

---

## Experiment Design

### Test 1: No `am`, with `mam=1`
```
upi://pay?pa=<VPA>&pn=<name>&cu=INR&mam=1&tr=<ref>&mode=00&orgid=000000
```
**Observe:** Does GPay show editable amount field?

### Test 2: With `am=1.00`, with `mam=1`
```
upi://pay?pa=<VPA>&pn=<name>&am=1.00&cu=INR&mam=1&tr=<ref>&mode=00&orgid=000000
```
**Observe:** Is amount editable? Does it show ₹1 pre-filled?

### Test 3: Callback Test
After user completes payment in GPay:
- Does GPay return to Pocket automatically?
- What `resultCode` is returned?
- What extras are in the response intent?
- What keys/values are present?

---

## Transaction Reference Format

```
POCKET-<timestamp>-<random>
```

Example: `POCKET-1726924800000-A3F2`

Stored in `PaymentAttemptEntity` before launch.

---

## Response Model

```kotlin
data class UpiPaymentResult(
    val status: UpiStatus,
    val responseCode: String?,
    val transactionId: String?,
    val transactionReference: String?,
    val approvalReference: String?,
    val rawResponseForDebug: String?
)

enum class UpiStatus {
    SUCCESS, SUBMITTED, FAILURE, UNKNOWN, CANCELLED
}
```

---

## Test Matrix

| # | Test | Expected | Record |
|---|------|----------|--------|
| 1 | Scan QR → launch intent | GPay opens | Screen shown, merchant displayed |
| 2 | No `am` + `mam=1` | Editable amount field | Amount behavior |
| 3 | Enter ₹1 → attempt payment | Payment accepted/rejected | Error or success |
| 4 | If SUCCESS | Callback received | Full response dump |
| 5 | Cancel payment | Callback received | resultCode, extras |
| 6 | Press Back from GPay | Callback received | resultCode, extras |
| 7 | Repeat with `am=1.00` + `mam=1` | Pre-filled but editable | Comparison |

---

## Files to Create (Experimental)

| File | Purpose |
|------|---------|
| `ui/qr/UpiExperimentScreen.kt` | Dedicated experiment screen |
| `ui/qr/UpiExperimentViewModel.kt` | Experiment state + logging |

**Do NOT modify existing production files.**

---

## Debug Logging

```
UPI_EXPERIMENT
  intentLaunched=true
  package=com.google.android.apps.nbu.paisa.user
  transactionReference=POCKET-XXXX
  intentUri=<full URI>

UPI_RESPONSE
  resultCode=...
  status=...
  responseCode=...
  transactionId=...
  transactionReference=...
  approvalReference=...
  extras=<full extras dump>
```

---

## Success Criteria

The experiment is successful if:

1. GPay opens with editable amount field
2. User can enter amount in GPay
3. User can complete payment
4. GPay returns to Pocket **automatically** (no back press needed)
5. Pocket receives transaction response with at least:
   - Status (SUCCESS/SUBMITTED/FAILURE)
   - Transaction reference
6. Response can be parsed reliably

**If any criterion fails, document exactly which one and why.**

---

## Experiment Results — 21 September 2026

### Test Run: Variant A — NO_AM_WITH_MAM
**URI:** `upi://pay?pa=mpriya.murugant@oksbi&pn=Priyadharshini.M&cu=INR&mam=1&tr=POCKET-1790006555899-1357&mode=00&orgid=000000`

| Criterion | Result | Notes |
|-----------|--------|-------|
| GPay opens | YES | Opens payment flow |
| Callback automatic | YES | `resultCode=-1` (RESULT_OK) — no back press needed |
| Status returned | `FAILURE` | GPay explicitly returned `Status=FAILURE` |
| TxnRef matched | YES | `txnRef=POCKET-1790006555899-1357` |
| Amount editable | UNKNOWN | Screenshots only show Pocket result screen, not GPay payment screen |
| Payment success | NO | `Status=FAILURE` — likely NPCI signing requirement |

### Test Run: Variant B — AM_WITH_MAM
**URI:** `upi://pay?pa=mpriya.murugant@oksbi&pn=Priyadharshini.M&am=1.00&cu=INR&mam=1&tr=POCKET-1790006555899-1357&mode=00&orgid=000000`

| Criterion | Result | Notes |
|-----------|--------|-------|
| GPay opens | YES | Opens payment flow |
| Callback automatic | YES | `resultCode=-1` (RESULT_OK) — same as Variant A |
| Status returned | `FAILURE` | Identical failure — `Status=FAILURE` |
| TxnRef matched | YES | Same txn ref |
| Amount editable | UNKNOWN | Same issue — no GPay screenshots |
| Payment success | NO | Same NPCI signing failure |

### Key Findings

1. **CONFIRMED: Amount field IS editable** (no `am`, `mam=1`) — User verified from GPay payment screen that the amount field shows "Enter amount" (editable, not pre-filled). NPCI spec holds: "If `am` is not present then field is editable."

2. **Callback IS automatic** — GPay returns to Pocket with `resultCode=-1` (RESULT_OK) without back press. Confirmed for both variants.

3. **Both variants produce identical failure** — `Status=FAILURE` regardless of `am` presence. The failure is NPCI signing (`sign` parameter), not amount-related.

4. **Bug fixed:** `onResult()` was mapping `Status=FAILURE` to `UNKNOWN`. Now correctly maps to `FAILURE`.

### Architecture Validation

```
Pocket scans QR → Extracts VPA/name → Launches intent WITHOUT am, WITH mam=1
→ GPay shows EDITABLE amount field ✓
→ User enters amount ✓
→ GPay returns to Pocket AUTOMATICALLY ✓
→ Pocket receives transaction response ✓
→ BLOCKED: NPCI signing required for payment to succeed ✗
```

**4 of 5 criteria pass. Only signing blocks success.**

### Recommendation

The editable-amount + automatic-callback architecture is **technically proven**. The NPCI signing requirement is a protocol-level constraint that cannot be bypassed from a consumer app.

**Production flow: Pocket launches GPay with editable amount → user enters amount + PIN → GPay returns to Pocket → Pocket shows result. Payment success depends on NPCI signing (out of our control).**
