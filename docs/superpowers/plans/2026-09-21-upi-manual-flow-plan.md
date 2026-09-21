# UPI Payment — Manual Flow Plan

> **Date:** 21 September 2026  
> **Goal:** Scan QR → Open UPI app → User pays manually → Return to Pocket → Record expense  
> **Key:** No pre-fill. No intent errors. Clean flow.

---

## Problem With Current Approach

Current flow tries to pre-fill amount via UPI intent:
1. Scan QR → enter amount → tap "Pay Securely"
2. GPay opens with amount pre-filled
3. **Payment fails** (NPCI signing requirement)
4. User sees error → confused

**The intent pre-fill causes friction**, not reduces it.

---

## New Flow: Manual Payment + Confirmation

### User Journey

```
┌─────────────────────────────────────────────────┐
│  1. SCAN QR                                     │
│     Camera scans UPI QR code                    │
│     Extracts: VPA, name                         │
└──────────────────┬──────────────────────────────┘
                   ▼
┌─────────────────────────────────────────────────┐
│  2. CATEGORY + AMOUNT                           │
│     Shows: VPA, name                            │
│     User selects category                       │
│     User enters amount (or skips for later)     │
└──────────────────┬──────────────────────────────┘
                   ▼
┌─────────────────────────────────────────────────┐
│  3. OPEN UPI APP                                │
│     Tap "Open [GPay/Paytm]"                     │
│     UPI app opens (home screen, no pre-fill)    │
│     User pays manually in UPI app               │
└──────────────────┬──────────────────────────────┘
                   ▼
┌─────────────────────────────────────────────────┐
│  4. RETURN TO POCKET                            │
│     User presses back / switches to Pocket      │
│     Pocket detects return                       │
└──────────────────┬──────────────────────────────┘
                   ▼
┌─────────────────────────────────────────────────┐
│  5. CONFIRM AMOUNT                              │
│     "What amount did you pay?"                  │
│     Shows merchant name for reference           │
│     User enters amount                          │
│     Tap "Confirm"                               │
└──────────────────┬──────────────────────────────┘
                   ▼
┌─────────────────────────────────────────────────┐
│  6. EXPENSE RECORDED                            │
│     Amount + category + VPA saved               │
│     Reflected on dashboard, calendar, insights  │
│     Navigate to home                            │
└─────────────────────────────────────────────────┘
```

---

## Screen Breakdown

### Screen 1: QR Scanner (Existing)
- Camera scans QR
- Parses VPA, name
- Navigates to Screen 2

### Screen 2: Payment Setup (Modified)
**Current:** Shows VPA, name, amount input, category, "Pay Securely" button  
**New:** Shows VPA, name, category selector, amount input (optional), "Open UPI App" button

**Layout:**
```
┌──────────────────────────────────┐
│  ← Pay                          │
│                                  │
│  ┌────────────────────────────┐  │
│  │  👤                        │  │
│  │  Priyadharshini.M          │  │
│  │  mpriya.murugant@oksbi     │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │  Category                  │  │
│  │  [Food] [Groceries] [Bills]│  │
│  │  [Transport] [Shopping]    │  │
│  │  [Other]                   │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │  Amount (optional)         │  │
│  │  ₹ [                ]      │  │
│  │  Skip if you'll enter      │  │
│  │  in UPI app                │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │    Open Google Pay         │  │
│  └────────────────────────────┘  │
│                                  │
│  Paying with GPay · Change       │
└──────────────────────────────────┘
```

**Behavior:**
- Category is **required** (for expense tracking)
- Amount is **optional** — user can enter now or after payment
- "Open UPI App" button opens the UPI app (home screen, no intent params)
- No `upi://pay` intent — just `getLaunchIntentForPackage()`

### Screen 3: Amount Confirmation (New)
**Shows after user returns from UPI app.**

**Layout:**
```
┌──────────────────────────────────┐
│  ← Confirm Payment              │
│                                  │
│  ┌────────────────────────────┐  │
│  │  👤                        │  │
│  │  Priyadharshini.M          │  │
│  │  mpriya.murugant@oksbi     │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │  How much did you pay?     │  │
│  │  ₹ [                ]      │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │  Category: Food            │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │      Confirm               │  │
│  └────────────────────────────┘  │
│                                  │
│  Skip · Go to Home               │
└──────────────────────────────────┘
```

**Behavior:**
- If user entered amount in Screen 2 → pre-fill here
- If user skipped → empty input
- "Confirm" → records expense → navigates home
- "Skip" → discards → navigates home
- Shows merchant name for reference

---

## Technical Implementation

### Modified Files

| File | Change |
|------|--------|
| `PaymentSetupScreen.kt` | Remove intent logic, add "Open App" button, make amount optional |
| `PaymentSetupViewModel.kt` | Remove intent launch, add app open + return detection + amount confirm |
| `PaymentSetupUiState.kt` | Add `showConfirmSheet` state, remove `launchIntent` |
| `Routes.kt` | Add `PAYMENT_CONFIRM` route (or reuse existing) |
| `PocketNavHost.kt` | Wire confirm screen |

### New State Flow

```
PaymentSetupViewModel:
  - openUpiApp(context, packageName) → launches UPI app home
  - onUserReturned() → shows confirm sheet
  - confirmAmount(amount) → records expense, navigates home
  - skipPayment() → discards, navigates home
```

### App Launch (No Intent)

```kotlin
fun openUpiApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        context.startActivity(intent)
    }
}
```

This just opens the UPI app's home screen. No `upi://pay` intent. No parameters. User navigates to send/pay manually.

### Return Detection

Use `LifecycleEventObserver` to detect when user returns to the app:

```kotlin
LaunchedEffect(Unit) {
    lifecycleEventObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.onUserReturned()
        }
    }
    lifecycle.addObserver(lifecycleEventObserver)
}
```

When user switches back to Pocket after paying, the confirm sheet appears.

### Expense Recording

```kotlin
fun confirmAmount(amountPaise: Long) {
    // Record expense with:
    // - VPA from scanned QR
    // - Amount from user input
    // - Category from selection
    // - Timestamp
    // Navigate to home
}
```

---

## Two Approaches to Test

### Approach A: Category First, Then UPI App
1. Scan QR → select category → open UPI app
2. User pays in UPI app
3. Return to Pocket → enter amount → confirm
4. Expense recorded

**Pros:** Category captured before payment (good for tracking)  
**Cons:** User must remember category

### Approach B: Amount First, Then UPI App
1. Scan QR → enter amount → select category → open UPI app
2. User pays in UPI app (amount already known)
3. Return to Pocket → confirm
4. Expense recorded

**Pros:** Amount captured upfront (no re-entry)  
**Cons:** If user pays different amount, must edit

### Recommendation: **Approach A**

- Less friction before opening UPI app
- Amount confirmation is one step
- Category is the only thing to remember (and it's shown on confirm screen)

---

## Expected Outcome

1. **No errors** — UPI app opens normally, no intent rejection
2. **Clean flow** — scan → category → pay → confirm → done
3. **Expense tracking works** — amount + category recorded
4. **Dashboard reflects** — new expense shows on home, calendar, insights
5. **Low friction** — 3 taps to complete (scan → category → confirm)

---

## Files to Create/Modify

| File | Action |
|------|--------|
| `ui/qr/PaymentSetupScreen.kt` | Modify — remove intent, add open app, make amount optional |
| `ui/qr/PaymentSetupViewModel.kt` | Modify — remove intent logic, add return detection + confirm |
| `ui/qr/PaymentConfirmSheet.kt` | **New** — amount confirmation bottom sheet |
| `navigation/PocketNavHost.kt` | Modify — wire new flow |
| `navigation/Routes.kt` | Modify — update routes if needed |
