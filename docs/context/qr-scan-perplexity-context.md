# QR-Scan Deep-Research Context (for Perplexity)

Paste this file plus `qr-scan-chronicle.md` (same folder) as context. Goal: find
out whether ANY legal, working path exists in 2026 for a non-PSP personal
expense app to initiate or verify UPI payments via third-party PSP apps —
or confirm definitively that none exists.

## 1. What was built and tried (verified facts, Sept 2026)

- App: Pocket, offline Android expense tracker (Kotlin, minSdk 26). Full code on
  branch `qr-scan` (commit `4ccea0a`).
- Launch mechanism: `Intent(ACTION_VIEW, upi://pay?...)` targeted at PSP packages
  (`com.google.android.apps.nbu.paisa.user`, `net.one97.paytm`,
  `com.phonepe.app`, `in.org.npci.upiapp`) + system chooser fallback; manifest
  `<queries>` for all four (required on Android 11+ for package visibility).
- URI construction: manual `StringBuilder`, per-param `URLEncoder.encode()`
  (`Uri.Builder` double-encodes `@` → `%40` and breaks parsing). Params tried in
  combinations: `pa`, `pn`, `am`, `cu=INR`, `mam=1`, `tr`, `tid`, `mc`, `tn`,
  `mode=00`, `mode=02`, `orgid=000000`.
- Confirmed working: QR parse/validate (VPA regex, amount regex, PERSONAL /
  MERCHANT_STATIC / MERCHANT_DYNAMIC classification, `sign` presence detection);
  GPay opens with editable amount when `am` absent + `mam=1`; GPay auto-returns
  with `resultCode=-1`; BHIM biometric step passes.
- Confirmed failing everywhere: the payment itself. GPay: "bank limit exceeded";
  Paytm: "UPI risk policy"; PhonePe: "risky transaction"/silent block; BHIM past
  biometric: "request type is not supported". No amount is ever returned in the
  callback. Personal (unsigned, no-`am`) QRs always rejected.
- Root-cause model: NPCI UPI Linking Spec v1.6 §`sign` (M-Mandatory, Base64 RSA
  signature over the intent string; public key registered via acquiring bank's
  Manage VAE API; PSP verifies per transaction). No key → reject. App
  constraints held: no INTERNET permission, no SMS/notification/accessibility
  scraping, no PIN/bank contact, integer-paise INR only.

## 2. Environment anchors (for version-sensitive answers)

- Test window Sept 2026; emulator + physical device, India UPI ecosystem.
- CameraX 1.4.1, ML Kit barcode-scanning 17.3.0, navigation-compose 2.8.9,
  AGP 9.4.0, compile/target SDK 36.

## 3. Research questions (answer each with sources, dated 2025–2026)

1. TPAP/third-party intent rules: as of late 2026, is there ANY compliant way
   for a non-PSP, non-TPAP app to fire a `upi://pay` intent that a major PSP
   (GPay/PhonePe/Paytm/BHIM) will actually authorize — e.g. P2P/person-to-person
   collect exemptions, `mode`/`orgid`/`submerchant` combinations, or unsigned
   small-value allowances? Cite NPCI circulars or PSP docs, not blogs.
2. `sign` parameter reality check: our finding is that all PSPs hard-reject
   unsigned intents despite the spec's "warn + allow passcode" fallback. Any
   2025–2026 evidence (NPCI circular, PSP changelog) changing this?
3. UPI versions/circulars: any UPI 2.x/3.0, e-RUPI, UPI Lite/LiteX, or 2026
   NPCI circular touching third-party intent initiation, Collect deprecation
   timelines (post-Feb-2026 mandate noted), or new verification APIs usable
   without PSP registration?
4. Alternatives without registration: verification-only approaches (e.g. UPI
   VPA verification/verifyWebhook-style APIs, Autopay mandate verification)
   accessible to individuals; SDK-less collect-request flows; per-transaction
   fee-free options. Exclude anything needing business PAN/GST or a gateway
   contract — or price those paths exactly if they are the only way.
5. Precedent: how do existing personal expense apps (e.g. Splitwise-style or
   India budgeting apps) auto-capture UPI spends in 2026 — SMS parsing
   (needs SMS permission), bank-statement import, or manual entry? Which is
   dominant and why?

## 4. Output format requested

For each question: verdict (possible / impossible / conditional), the exact
mechanism or the exact blocker (spec section, circular number, or API name),
dated sources, and what we should build, change, or permanently drop in Pocket
as a consequence. Flag anything that contradicts §1 findings with evidence.
