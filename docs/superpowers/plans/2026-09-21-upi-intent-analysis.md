# UPI Intent Payment Integration — Technical Analysis & Feasibility Report

**Project:** Pocket — Personal Expense Manager  
**Date:** 21 September 2026  
**Prepared for:** Client Review

---

## Executive Summary

Pocket's QR-based UPI payment initiation feature encounters **systematic rejection** by all major UPI apps (Google Pay, Paytm, PhonePe) when attempting to pre-fill payment details via Android intents. This is **not a code defect** — it is a deliberate security restriction enforced by NPCI (National Payments Corporation of India) and implemented by every Payment Service Provider (PSP) app.

**Root cause:** NPCI mandates cryptographic digital signatures on all merchant-initiated UPI intents. Without this signature, PSP apps treat the payment request as untrusted and block it.

**Impact:** Pocket cannot directly initiate UPI payments that reach the UPI PIN page with pre-filled details. The feature works only when scanning QR codes that already contain a valid signature (business/merchant QR codes).

---

## 1. What We Built

### Feature Flow
```
Scan QR → Parse VPA/amount → User enters amount → Select category
→ Tap "Pay Securely" → UPI app opens with pre-filled details → User enters UPI PIN
```

### Technical Implementation
- **QR Scanner:** CameraX + ML Kit barcode scanning (on-device, no internet)
- **QR Parser:** Pure Kotlin parser for `upi://pay` URIs
- **Intent Builder:** Constructs `upi://pay` intents with all NPCI parameters
- **Fallback:** When UPI app rejects, shows copy-VPA + manual-entry UI

---

## 2. Why It Fails — The NPCI Signature Requirement

### 2.1 The `sign` Parameter (NPCI UPI Linking Spec v1.6/v1.7)

NPCI's UPI Linking Specification mandates that **all** intent-based payment requests must include a `sign` parameter — a Base64-encoded digital signature. This is classified as **M-Mandatory** for both merchant-initiated and PSP-initiated transactions.

| Parameter | Status | Description |
|-----------|--------|-------------|
| `pa` (Payee VPA) | M-Mandatory | Payee Virtual Payment Address |
| `pn` (Payee Name) | M-Mandatory | Registered payee name |
| `mc` (Merchant Code) | Optional | Merchant category code |
| `tr` (Transaction Ref) | C-Mandatory | Unique per transaction |
| `am` (Amount) | M-Mandatory | Payment amount |
| `cu` (Currency) | M-Mandatory | Always INR |
| `sign` (Signature) | **M-Mandatory** | **SHA256+RSA512 digital signature** |
| `orgid` (Org ID) | M-Mandatory | `000000` for merchant-initiated |

**Source:** NPCI UPI Linking Specifications v1.6, Section 1.2 — "sign String M M — Base 64 encoded Digital signature needs to be passed in this tag"

### 2.2 How Signature Verification Works

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Merchant    │     │  Acquiring   │     │   UPI NPCI  │
│  App         │     │  Bank        │     │   Switch    │
└──────┬──────┘     └──────┬───────┘     └──────┬──────┘
       │                   │                     │
       │ 1. Generate key   │                     │
       │    pair (RSA512)  │                     │
       │──────────────────>│                     │
       │                   │ 2. Upload public    │
       │                   │    key via          │
       │                   │    Manage VAE API   │
       │                   │────────────────────>│
       │                   │                     │
       │ 3. Sign intent    │                     │
       │    with private   │                     │
       │    key            │                     │
       │    (SHA256+RSA512)│                     │
       │                   │                     │
       │ 4. Send signed    │                     │
       │    intent to PSP  │                     │
       │────────────────────────────────────────>│
       │                   │                     │
       │                   │ 5. PSP downloads    │
       │                   │    merchant public  │
       │                   │    key from UPI     │
       │                   │<────────────────────│
       │                   │                     │
       │                   │ 6. Verify signature │
       │                   │    If valid →       │
       │                   │    bypass passcode  │
       │                   │    If invalid →     │
       │                   │    DECLINE          │
```

### 2.3 What Happens Without `sign`

Per NPCI spec Section 1.3:

> "If signature is not present in intent then the application should show warning message to user that the 'source of intent could not be verified' and shall request for passcode to proceed with the payment."

In practice, **modern PSP apps (2024-2026) have gone further** — they outright **reject** unsigned intents rather than just showing a warning:

| PSP App | Behavior for Unsigned Intents |
|---------|-------------------------------|
| **Google Pay** | "You have exceeded the bank limit for this payment" (generic error) |
| **Paytm** | "Payment failed as per our UPI risk policy to safeguard your account" |
| **PhonePe** | "This transaction may be risky" / blocks silently |

These are **intentional security measures**, not bugs. The apps classify unsigned intents as potentially fraudulent.

---

## 3. What Would Work

### Option A: NPCI Merchant Registration + Payment Gateway

**Requirements:**
1. Register as a merchant with a PSP bank (HDFC, ICICI, SBI, etc.)
2. Obtain a Merchant UPI ID (not a personal UPI ID)
3. Generate RSA512 key pair
4. Upload public key to UPI via the acquiring bank's `Manage VAE API`
5. Sign every intent with the private key before sending to PSP app
6. Pass `orgid` (assigned by NPCI) instead of `000000`

**Process:**
```
1. Apply to bank for merchant UPI ID
   → Bank verifies business registration (GST, PAN, etc.)
   → Takes 2-4 weeks

2. Generate RSA512 key pair
   → Private key stored securely in app/backend
   → Public key shared with bank

3. Bank uploads public key to NPCI
   → NPCI stores in List VAE cache
   → Updated daily by PSP servers

4. Sign every UPI intent
   → SHA256 of intent string (excluding &sign=)
   → Encrypt with private key (RSA512)
   → Append as &sign=<base64>

5. PSP app verifies signature
   → Downloads public key from NPCI
   → Verifies signature
   → If valid: shows payment page with pre-filled details
```

**Estimated Cost:** ₹5,000–50,000 setup + per-transaction fees  
**Timeline:** 4-8 weeks  
**Feasibility for personal expense tracker:** **Not viable** — requires business registration

### Option B: Payment Gateway SDK (Razorpay / Cashfree / Juspay)

**How it works:**
1. Merchant registers with Razorpay/Cashfree
2. Payment gateway handles all NPCI compliance:
   - Merchant registration with PSP bank
   - Key pair generation and management
   - Intent signing on gateway's server
   - Transaction status tracking
3. App calls gateway API → receives signed intent URI → launches UPI app

**Requirements:**
- Business PAN card
- GST registration (or sole proprietorship)
- Bank account in business name
- KYC verification (2-5 business days)

**Estimated Cost:**
- Razorpay: 2% per transaction, no setup fee
- Cashfree: 1.9% per transaction, no setup fee
- Juspay: Custom pricing

**Timeline:** 1-2 weeks for onboarding  
**Feasibility:** **Viable** if Pocket is商业化 — requires business entity

### Option C: Current Approach — Best-Effort with Fallback (What We Have Now)

**How it works:**
1. Scan QR → parse VPA/amount → build intent with all NPCI params
2. Launch UPI app (may work for signed QR codes, will fail for personal QR)
3. If rejected → show fallback UI:
   - Display merchant name, VPA, amount
   - "Copy VPA" button
   - "Open UPI App" button (opens app home for manual entry)
   - "Mark as Paid" button (records expense manually)

**Pros:**
- Zero cost, no registration required
- Works with signed merchant QR codes (some success)
- Graceful degradation with fallback UI
- Expense tracking still works (manual mark-as-paid)

**Cons:**
- Personal QR codes will always fail (no signature)
- GPay/Paytm show error messages to user
- UX friction — user must manually complete payment

### Option D: Copy VPA + Deep Link to UPI App

**How it works:**
1. Scan QR → parse VPA/amount
2. Copy VPA to clipboard
3. Open UPI app home screen
4. User pastes VPA, enters amount manually

**Pros:**
- No signature required
- No error messages from PSP apps
- Clean UX flow

**Cons:**
- Most manual steps
- User must re-enter amount

---

## 4. Recommendation

### For Current Phase (Personal Use / MVP): **Option C (Current Implementation)**

The existing best-effort approach with fallback is the correct solution for a personal expense tracker:

1. **Scanning business QR codes** → intent works (signature is in QR)
2. **Scanning personal QR codes** → intent fails gracefully → fallback UI
3. **Expense tracking** → always works via manual mark-as-paid
4. **No cost** → no merchant registration needed

### For Future Commercial Version: **Option B (Payment Gateway SDK)**

If Pocket is commercialized:
1. Register as business entity
2. Integrate Razorpay or Cashfree SDK
3. Handle NPCI compliance through the gateway
4. Full UPI intent flow with pre-filled details

---

## 5. Technical Details for Developer Reference

### 5.1 UPI Intent Parameter Requirements (NPCI v1.6/v1.7)

| Parameter | Required | Description |
|-----------|----------|-------------|
| `pa` | Yes | Payee VPA (e.g., `user@bank`) |
| `pn` | Yes | Payee registered name |
| `am` | Yes | Amount in decimal (e.g., `100.00`) |
| `cu` | Yes | Currency (`INR`) |
| `sign` | **Yes** | SHA256+RSA512 signature of intent string |
| `orgid` | Yes | `000000` for merchant, PSP org ID for PSP-initiated |
| `mc` | Optional | Merchant category code |
| `tr` | Conditional | Transaction reference (mandatory for P2M) |
| `tid` | Optional | Transaction ID (PSP-generated) |
| `tn` | Optional | Transaction note |
| `mode` | Optional | `00`=Default, `01`=QR, `02`=Secure QR, `04`=Intent, `05`=Secure Intent |

### 5.2 Why "Limit Exceeded" Is a Misleading Error

The "Limit Exceeded" / "UPI risk policy" errors are **not about financial limits**. They are generic security fallbacks:

- Missing `sign` → PSP cannot verify source → treated as potential fraud
- Missing `tr` in P2M → PSP cannot deduplicate → treated as replay attack
- Malformed URI → PSP parsing fails → generic error displayed
- Personal VPA used for merchant transaction → bank rejects

### 5.3 What Works Today

| Scenario | Works? | Reason |
|----------|--------|--------|
| Business QR (signed) + GPay | Sometimes | QR contains valid signature |
| Business QR (signed) + Paytm | Usually | Paytm more lenient with signed QR |
| Personal QR + any app | **No** | No signature in personal QR |
| Intent without `sign` | **No** | NPCI mandates signature |
| Payment Gateway SDK | **Yes** | Gateway signs on merchant's behalf |
| Copy VPA + manual entry | **Yes** | No intent used |

---

## 6. References

1. **NPCI UPI Linking Specifications v1.6** — Mandatory `sign` parameter
2. **NPCI UPI Linking Specifications v1.7 (Draft)** — Updated signing requirements
3. **NPCI UPI InfoSec Compliance Framework 2025** — Security audit requirements
4. **NPCI OC-215/2025-26** — API security guidelines, rate limiting
5. **Google Pay for India — In-App Payments** — Required parameters (`pa`, `pn`, `mc`, `tr`, `am`, `cu`)
6. **Razorpay UPI Intent Docs** — Payment gateway integration approach
7. **Cashfree UPI Intent Docs** — Alternative gateway approach
8. **Juspay UPI Merchant Stack** — Register Intent API flow

---

## 7. Conclusion

The UPI intent payment feature in Pocket **works correctly within NPCI's security constraints**. The rejection by GPay/Paytm is **by design** — a security measure to prevent fraudulent payment initiation from unregistered third-party apps.

**The app cannot bypass this restriction without:**
- NPCI merchant registration (requires business entity)
- A Payment Gateway integration (Razorpay/Cashfree)
- Cryptographic key management infrastructure

**The current best-effort + fallback approach is the optimal solution** for a personal expense tracker. It provides:
- Seamless experience for signed merchant QR codes
- Graceful degradation for personal QR codes
- Complete expense tracking regardless of payment success
