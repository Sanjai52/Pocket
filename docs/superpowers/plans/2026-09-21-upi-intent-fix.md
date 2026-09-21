# UPI Intent Fix — Best-Effort with Graceful Fallback

> **Status:** Pending approval
> **Approach:** Option A — Best-effort intent + fallback VPA copy

## Problem

UPI apps (GPay, Paytm) reject intent-based payments from third-party apps because:
1. **Missing `sign` parameter** — NPCI requires a cryptographic signature for all merchant-initiated intents. Without it, PSP apps treat the request as untrusted.
2. **Personal QR codes** lack merchant codes (`mc`) and signatures, making them incompatible with intent-based flows.
3. GPay/Paytm show generic errors ("UPI risk policy", "bank limit exceeded") for unsigned intents.

**Bottom line:** Without NPCI merchant registration + a Payment Gateway, we **cannot** make UPI apps process payments from our constructed intents.

## Solution: Best-Effort Intent + Fallback

### Strategy
1. **For signed QR codes** (business/merchant QR with `sign` param): Pass raw URI through — most apps accept it
2. **For unsigned QR codes** (personal QR): Try intent with all NPCI params, but expect rejection → show fallback UI
3. **Fallback UI**: Show merchant name, VPA, amount with a "Copy VPA" button and one-tap app open

### Changes Required

#### 1. Fix intent construction (`UpiIntentBuilder.kt`)
- Use `Intent.ACTION_VIEW` with `Uri.parse()` directly (not `Uri.Builder`)
- Remove incorrect `mode=02` and `orgid=000000` — these are only valid for merchant-registered apps
- Keep `mc`, `tr`, `tid`, `pn`, `pa`, `am`, `cu` per NPCI spec
- Remove `mode` and `orgid` entirely (they cause rejection when app isn't registered)

#### 2. Fix pass-through logic (`PaymentSetupViewModel.kt`)
- If QR has `sign` parameter → pass raw URI as-is (already works for most apps)
- If QR has `am` parameter → pass raw URI as-is
- If QR has no `am` → rebuild with amount using correct params
- Never add `mode` or `orgid` (app isn't registered)

#### 3. Add payment result fallback (`PaymentSetupScreen.kt`)
- After `upiLauncher` returns, if result indicates failure/cancel → show fallback sheet
- Fallback sheet contains:
  - Merchant name + VPA + amount
  - "Copy VPA" button (copies VPA to clipboard)
  - "Open [App]" button (opens UPI app home screen)
  - "Mark as paid" button (records expense manually)

#### 4. Add clipboard helper to `PaymentSetupViewModel.kt`
- `copyVpaToClipboard(context)` — copies VPA to clipboard
- `openUpiAppHome(context, packageName)` — launches UPI app home screen

### Files to Modify

| File | Change |
|------|--------|
| `data/qr/UpiIntentBuilder.kt` | Remove `mode`, `orgid`; fix URI construction |
| `ui/qr/PaymentSetupViewModel.kt` | Fix pass-through logic; add clipboard/home launch |
| `ui/qr/PaymentSetupScreen.kt` | Add fallback sheet after failed UPI result |
| `ui/qr/ResultSheet.kt` | Update failure sheet with VPA copy + app open |

### Test Scenarios

1. **Signed merchant QR** → Raw URI pass-through → Payment page opens in UPI app
2. **Personal QR (no amount)** → Intent with amount → GPay opens (may fail) → Fallback shows VPA copy
3. **Personal QR (with amount)** → Raw URI pass-through → App opens
4. **UPI app not installed** → Toast error
5. **User cancels in UPI app** → Fallback sheet shown
