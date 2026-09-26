# QR-Scan Feature Chronicle — Pocket Expense Tracker

Date written: 2026-09-24 (events: 19–21 Sept 2026). Branch with all code: `qr-scan`
(single commit `4ccea0a` on top of `main@c78fbdb`; pushed to origin, never merged).
Prior art on that branch: `docs/qr-feature.md`,
`docs/superpowers/plans/2026-09-21-qr-pay-complete-chronicle.md` + 6 related plan docs.

## 1. What the feature was about

Kill the friction of manual expense entry. At payment time the user scans the
merchant's UPI QR inside Pocket; the app extracts payee + amount, the user picks
a category once (remembered per merchant afterwards), pays in their UPI app, and
Pocket records a fully categorized expense on return — no typing amounts or
remembering to log later.

## 2. What we planned vs what we achieved

| Planned | Achieved |
|---|---|
| Scan merchant QR (CameraX + ML Kit) | ✅ Built and working |
| Parse/validate VPA, name, amount (`UpiQrParser.kt`: kind detection PERSONAL / MERCHANT_STATIC / MERCHANT_DYNAMIC, VPA + amount regex, signature presence flag) | ✅ Built, JVM-testable |
| Payment Setup screen (merchant, locked amount, category + memory) | ✅ Built |
| UPI app picker overlay (GPay, PhonePe, Paytm, BHIM) + default-app setting | ✅ Built |
| Launch `upi://pay` intent with pre-filled details (`UpiIntentBuilder.kt`, manual StringBuilder + URLEncoder) | ✅ Launches; payment itself blocked (see §4) |
| Parse PSP result callback | ✅ Automatic callback works (`resultCode=-1`), but GPay returns no amount |
| Record expense + payment-attempt tracking + merchant category memory (DB: `payment_attempts`, `merchant_category_memory` tables, new columns) | ✅ Built |
| Result sheets, payments-to-confirm list, fallback UX | ✅ Built |
| **End-to-end: scan → pay succeeds → auto-logged expense** | ❌ Blocked — NPCI signature requirement |

Production fallback shipped on the branch instead: post-payment manual amount
entry (pay in GPay with editable amount via `mam=1`, return, type the amount,
expense saved).

## 3. How it actually went — iterations and problems

- **Uri.Builder double-encoding:** `@` in VPAs became `%40`, GPay showed home.
  Fixed with manual `StringBuilder` + per-param `URLEncoder.encode()`.
- **8 GPay intent iterations:** added `mc`/`tid`, `mode=02`+`orgid` (fell back to
  home — secure-QR path needs merchant registration), removed them, tried
  `mode=00`, built fallback/result sheets. Every variant opened GPay fine but
  payments failed.
- **Editable-amount experiment:** confirmed `mam=1` without `am` gives an
  editable field and GPay auto-returns with `resultCode=-1` — 4 of 5 success
  criteria passed; only the payment itself failed.
- **Multi-PSP testing:** PhonePe and Paytm reject unsigned intents with
  misleading errors ("bank limit exceeded", "risk policy"); BHIM needed
  manifest `<queries>` + `in.org.npci.upiapp`, passed biometric, then
  "request type is not supported".
- **Root cause (verified across GPay/PhonePe/Paytm/BHIM):** NPCI UPI Linking Spec
  v1.6 makes the RSA512 `sign` parameter **mandatory** on intent requests
  (public key registered via acquiring bank; PSP verifies per transaction).
  Without it every PSP rejects — the spec's "warn + allow passcode" fallback is
  implemented by none of them. A personal tracker cannot obtain signing keys
  (needs business PAN/GST/bank + 4–8 weeks + fees); gateway SDKs need a business
  entity + ~2% per transaction.
- **Constraints we held throughout:** integer-paise INR only, no INTERNET
  permission, no SMS/notification/accessibility scraping, Pocket never touches
  UPI PINs or banks. These closed off every workaround that could have faked
  confirmation (SMS parsing, notification listening).

## 4. Outcome and where things stand

Feature abandoned on `main`; full implementation preserved on `qr-scan`
(58 files, ~10.5k insertions: `data/qr/`, `ui/qr/`, DB migration, CameraX 1.4.1
+ ML Kit 17.3.0, manifest CAMERA permission + PSP `<queries>`, new routes
`qr/scanner`, `qr/setup/{uri}`, `qr/pending`, plus test screenshots and a
`web/` demo). Reusable regardless of outcome: `UpiQrParser` (validation +
kind/signature detection) and the category-memory + payments-to-confirm data
model. Next step per owner: deep research (see `qr-scan-perplexity-context.md`)
into whether any legal third-party intent path exists for non-PSP apps.
