# UPI Intent Payment — Full Debugging Chronicle

> **Date:** 21 September 2026  
> **Project:** Pocket — Personal Expense Manager  
> **Issue:** UPI payment initiation via Android intent — amount pre-fills but payment fails

---

## 1. Initial Goal

Build a QR-based UPI payment feature in Pocket:
1. Scan a UPI QR code
2. Parse VPA, name, amount
3. Pre-fill payment details in GPay/Paytm/PhonePe
4. User enters UPI PIN → payment completes
5. Expense recorded automatically

**Expected UX:** Scan → Enter amount → Tap Pay → UPI PIN page → Done

---

## 2. What We Built

### Core Components
| Component | File | Purpose |
|-----------|------|---------|
| QR Parser | `UpiQrParser.kt` | Parses `upi://pay` URIs from QR codes |
| Intent Builder | `UpiIntentBuilder.kt` | Constructs UPI payment intents |
| Scanner | `QRScannerScreen.kt` | CameraX + ML Kit barcode scanning |
| Payment Setup | `PaymentSetupScreen.kt` | Shows merchant info, amount, category |
| App Overlay | `PaymentAppOverlay.kt` | UPI app selection bottom sheet |
| Result Sheet | `PaymentResultFallbackSheet` | Post-payment result handling |
| ViewModel | `PaymentSetupViewModel.kt` | State management, intent launch |

### Intent Parameters Used
```
upi://pay?
  pa=<VPA>           // Payee address
  pn=<name>          // Payee name
  am=<amount>        // Amount (e.g., 450.00)
  cu=INR             // Currency
  mc=<merchant_code> // Merchant category code
  tr=<txn_ref>       // Transaction reference
  tid=<txn_id>       // Transaction ID
  mode=00            // Default transaction mode
  orgid=000000       // Merchant-initiated org ID
```

---

## 3. Iteration Log

### Iteration 1: Basic Intent (Initial Build)
**What we did:** Built `upi://pay` intent with `pa`, `pn`, `am`, `cu` parameters. Used `Uri.Builder` to construct the URI. Launched via `ActivityResultLauncher`.

**Result:** GPay opened but showed home screen — amount not pre-filled.

**Root cause:** `Uri.Builder` was double-encoding special characters in VPA (e.g., `@` in `user@bank` became `%40`). GPay couldn't parse the VPA.

---

### Iteration 2: Switched to Manual URI Construction
**What we did:** Replaced `Uri.Builder` with manual `StringBuilder` + `URLEncoder.encode()`. Built URI string directly.

```kotlin
val sb = StringBuilder()
sb.append("upi://pay?")
sb.append("pa=").append(URLEncoder.encode(payeeVpa, "UTF-8"))
sb.append("&am=").append(amount)
sb.append("&cu=INR")
```

**Result:** GPay opened with amount pre-filled. But payment failed with "You have exceeded the bank limit for this payment."

**Analysis:** URI parsing was correct — GPay received and displayed the amount. But the transaction was rejected at the risk check stage.

---

### Iteration 3: Added `mc`, `tid` Parameters
**What we did:** Added `mc` (merchant code) and `tid` (transaction ID) per NPCI spec. Google Pay docs list `mc` as required.

**Result:** Same error — "limit exceeded" on GPay, "UPI risk policy" on Paytm.

**Analysis:** Adding parameters didn't help. The rejection was at a deeper level — missing cryptographic signature.

---

### Iteration 4: Added `mode=02` and `orgid=000000`
**What we did:** Added `mode=02` (secure QR) and `orgid=000000` (merchant-initiated) per NPCI spec.

**Result:** GPay opened to HOME SCREEN instead of payment page. Amount not pre-filled at all.

**Root cause:** `mode=02` tells GPay this is a "secure QR" transaction requiring merchant registration. Without the merchant's public key (for signature verification), GPay falls back to home screen.

---

### Iteration 5: Removed `mode` and `orgid`
**What we did:** Removed `mode` and `orgid` to fix the home screen issue.

**Result:** Amount pre-filled again, but payment still failed with same errors.

**Analysis:** Removing `mode`/`orgid` fixed the display issue but not the transaction rejection.

---

### Iteration 6: Added `mode=00` and `orgid=000000`
**What we did:** Used `mode=00` (default transaction) instead of `mode=02`.

**Result:** Same errors persisted.

**Analysis:** The `mode` value doesn't affect the signature requirement. All modes require `sign` for merchant-initiated transactions.

---

### Iteration 7: Added Fallback UI
**What we did:** Added result sheet with:
- Copy VPA button
- Open UPI App button
- Mark as Paid button

**Result:** User still saw confusing error from GPay before the fallback appeared.

**Analysis:** The error was coming from GPay itself, not our app. Our fallback appeared after GPay closed.

---

### Iteration 8: Always Show Result Sheet
**What we did:** Changed `onUpiResult()` to always show result sheet regardless of result code. Updated copy to include VPA + amount + name.

**Result:** User now gets clear guidance after GPay error.

---

## 4. Research Findings

### 4.1 NPCI UPI Linking Spec v1.6/v1.7

The `sign` parameter is **M-Mandatory**:

> "sign String M M — Base 64 encoded Digital signature needs to be passed in this tag"

Without `sign`:
- PSP app should show warning "source of intent could not be verified"
- Should request passcode to proceed
- **In practice (2024-2026): PSP apps outright REJECT unsigned intents**

### 4.2 How Signature Works

```
1. Merchant generates RSA512 key pair
2. Public key uploaded to NPCI via acquiring bank's Manage VAE API
3. Merchant signs every intent: SHA256(intent_string) → RSA512(private_key)
4. PSP app downloads public key from NPCI
5. PSP verifies signature
6. If valid → proceed to UPI PIN
7. If invalid/missing → REJECT
```

### 4.3 PSP Error Messages

| PSP App | Error for Unsigned Intents |
|---------|---------------------------|
| Google Pay | "You have exceeded the bank limit for this payment" |
| Paytm | "Payment failed as per our UPI risk policy to safeguard your account" |
| PhonePe | "This transaction may be risky" / silent block |

These are **intentional security measures**, not bugs.

### 4.4 What Works

| Scenario | Works? | Why |
|----------|--------|-----|
| Business QR (signed) + GPay | Sometimes | QR contains valid signature |
| Business QR (signed) + Paytm | Usually | Paytm more lenient |
| Personal QR + any app | **No** | No signature in personal QR |
| Intent without `sign` | **No** | NPCI mandates signature |
| Payment Gateway SDK | **Yes** | Gateway signs on merchant's behalf |
| Copy VPA + manual entry | **Yes** | No intent used |

---

## 5. Why It's Impossible Without Merchant Registration

The NPCI protocol has three layers of verification:

```
Layer 1: URI Parsing
  → Our intent passes this (amount shows in GPay)
  ✓ WORKING

Layer 2: Risk Check
  → GPay checks for `sign` parameter
  → No signature = untrusted source
  ✗ BLOCKED

Layer 3: Transaction Processing
  → Bank validates merchant registration
  → No registration = invalid merchant
  ✗ BLOCKED
```

**We can't bypass Layer 2 or 3 without:**
1. NPCI merchant registration (business PAN + GST + bank account)
2. RSA512 key pair generation
3. Public key upload to NPCI via acquiring bank
4. Intent signing with private key

**Estimated setup:** ₹5,000–50,000 + 4-8 weeks

---

## 6. Current State

### What Works
- QR scanning and parsing ✓
- Amount pre-fill in GPay ✓
- Payment attempt recording ✓
- Fallback UI with copy/open/mark-paid ✓
- Category memory ✓
- Expense tracking ✓

### What Doesn't Work
- Payment completion via intent ✗ (NPCI signature requirement)
- Automatic expense recording after payment ✗ (depends on payment success)

### User Flow Now

```
1. Scan QR → Shows merchant name + VPA
2. Enter amount → Select category
3. Tap "Pay Securely"
4. GPay opens with amount pre-filled
5. User taps Pay → GPay shows error
6. GPay closes → Our result sheet appears:
   - "Payment not completed"
   - Merchant name + VPA + amount displayed
   - [Copy UPI Details] → copies to clipboard
   - [Open Google Pay] → opens GPay home
   - [Mark as Paid] → records expense manually
7. User pastes VPA in GPay → enters amount → pays
8. OR user marks as paid manually
```

---

## 7. Options Going Forward

### Option A: Accept Current State (Recommended for MVP)
- Keep pre-fill + fallback flow
- User sees error but gets clear guidance
- Expense tracking works via manual mark-as-paid
- **No cost, no registration**

### Option B: Payment Gateway SDK (For Commercial Version)
- Integrate Razorpay/Cashfree
- Handle signing through gateway
- Full working flow
- **Requires business entity + 2% per transaction**

### Option C: Remove Intent Entirely
- Skip intent → show VPA + amount + copy
- User opens UPI app manually
- No error messages
- **Cleanest UX but most manual steps**

---

## 8. Lessons Learned

1. **NPCI signing is non-negotiable** — no workaround exists
2. **PSP error messages are misleading** — "limit exceeded" means "unsigned intent"
3. **`mode` and `orgid` matter** — wrong values break display, right values don't fix signing
4. **Personal QR codes can never work** with intent-based flows
5. **Payment Gateway is the only path** for full UPI intent functionality
6. **Fallback UX is critical** — user must never be left confused after an error

---

## 9. Files Reference

| File | Purpose |
|------|---------|
| `data/qr/UpiIntentBuilder.kt` | Intent URI construction |
| `data/qr/UpiQrParser.kt` | QR code parsing |
| `data/qr/StatusEnums.kt` | Status enums (LaunchPlan, etc.) |
| `ui/qr/PaymentSetupViewModel.kt` | Payment state + intent launch |
| `ui/qr/PaymentSetupScreen.kt` | Payment UI + result sheet |
| `ui/qr/PaymentAppOverlay.kt` | UPI app selection |
| `ui/qr/ResultSheet.kt` | Success/failure sheets |
| `docs/superpowers/plans/2026-09-21-upi-intent-analysis.md` | NPCI analysis for client |
