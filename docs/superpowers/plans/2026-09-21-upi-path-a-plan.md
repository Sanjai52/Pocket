# UPI Payment — Path A: Pre-fill + Polished Fallback

> **Goal:** Amount pre-fills in GPay/Paytm. When payment fails at risk check, provide a seamless fallback that helps user complete payment manually.

## Current Problem

User sees this confusing flow:
1. GPay opens with ₹450 pre-filled → Good
2. User taps Pay → **"You have exceeded the bank limit"** → Confusing
3. GPay closes → No guidance on what to do next

## Solution: Polished Fallback UX

### Flow After Fix

```
Scan QR → Enter amount → Tap "Pay Securely"
  ↓
GPay opens with amount pre-filled
  ↓
User taps Pay → Error (NPCI signing block)
  ↓
GPay closes → Our app shows result sheet:
  ┌──────────────────────────────────────┐
  │  ⚠️  Payment not completed           │
  │                                      │
  │  Paying to: Priyadharshini.M         │
  │  UPI ID: mpriya.murugant@oksbi      │
  │  Amount: ₹450.00                     │
  │                                      │
  │  [ Copy UPI ID ]                     │
  │  [ Open Google Pay ]                 │
  │  [ Mark as Paid ]                    │
  │                                      │
  │  Go to Home                          │
  └──────────────────────────────────────┘
```

### Changes Required

#### 1. Result Sheet — Always Show After UPI Returns
**File:** `PaymentSetupViewModel.kt`

Currently: `showResultSheet` is set based on `resultCode`. Change to **always show** the result sheet after UPI app returns, regardless of result.

```
onUpiResult() → always set showResultSheet = true
```

#### 2. Result Sheet — Different Content Based on Result
**File:** `PaymentSetupScreen.kt` (PaymentResultFallbackSheet)

**If SUCCESS (resultCode OK + status=SUCCESS):**
- Green checkmark
- "Payment submitted"
- "Check your UPI app for confirmation"
- [Go to Home]

**If FAILED/CANCELED:**
- Orange warning icon
- "Payment not completed"
- "Complete payment manually in your UPI app"
- Merchant name + VPA + amount
- [Copy UPI ID] — copies VPA to clipboard
- [Open Google Pay] — opens GPay home screen
- [Mark as Paid] — records expense manually
- [Go to Home]

#### 3. Copy VPA — Also Copy Amount
**File:** `PaymentSetupViewModel.kt`

Update `copyVpaToClipboard()` to copy a formatted string:
```
UPI ID: mpriya.murugant@oksbi
Amount: ₹450.00
Name: Priyadharshini.M
```

User can paste this into GPay's "Enter UPI ID" field, and the amount is visible for manual entry.

#### 4. Open UPI App — Better Deep Link
**File:** `PaymentSetupViewModel.kt`

Instead of just opening GPay home, try to deep-link to the send/money screen:
- GPay: `gpay://upi/` or just launch package
- Paytm: `paytm://` or just launch package
- PhonePe: `phonepe://` or just launch package

#### 5. Remove Confusing Error Toast
**File:** `PaymentSetupScreen.kt`

Currently shows "No UPI app found" toast. Remove this — the result sheet handles all cases.

### Files to Modify

| File | Change |
|------|--------|
| `PaymentSetupViewModel.kt` | Always show result sheet; copy VPA+amount; better app launch |
| `PaymentSetupScreen.kt` | Polish result sheet UI; remove error toast |

### Expected Outcome

1. **Best case:** GPay opens → amount pre-filled → user pays → success
2. **Normal case:** GPay opens → amount pre-filled → user taps Pay → error → result sheet → user copies VPA → opens GPay → pastes VPA → enters amount → pays
3. **Worst case:** GPay opens → amount pre-filled → error → result sheet → user marks as paid manually

**Key improvement:** User is never left confused. Every outcome has clear next steps.
