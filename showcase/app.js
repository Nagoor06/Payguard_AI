"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const scenarios = {
    timeout: { state: "FAILED", code: "TIMEOUT", retries: 0, note: "A synthetic payment failed due to a gateway timeout." },
    fraud: { state: "FAILED", code: "FRAUD_REVIEW", retries: 0, note: "An example risk signal flagged this synthetic transaction." },
    pending: { state: "PENDING", code: "TIMEOUT", retries: 0, note: "The simulated payment is still pending settlement." },
    declined: { state: "FAILED", code: "DECLINED", retries: 0, note: "A synthetic transaction was declined by the gateway." },
    settled: { state: "SETTLED", code: "NONE", retries: 0, note: "A simulated payment has already settled." },
    exhausted: { state: "FAILED", code: "RATE_LIMITED", retries: 3, note: "The simulated payment already had three retry attempts." }
  };

  // This intentionally mirrors src/main/java/dev/nagoor/payguard/core/PaymentPolicy.java.
  // This public preview never makes an API call or persists a record.
  function evaluate(state, code, retries) {
    const states = ["FAILED", "PENDING", "SETTLED"];
    const codes = ["TIMEOUT", "RATE_LIMITED", "DECLINED", "FRAUD_REVIEW", "UNKNOWN", "NONE"];
    if (!states.includes(state) || !codes.includes(code) || !Number.isInteger(retries) || retries < 0 || retries > 10) {
      throw new Error("Select valid parameters and a retry count between 0 and 10.");
    }
    if (code === "FRAUD_REVIEW") {
      return { category: "HUMAN_REVIEW", retryCandidate: false, humanReviewRequired: true,
        nextStep: "Stop automated processing and request a risk-team review." };
    }
    if (state === "SETTLED") {
      return { category: "RESOLVED", retryCandidate: false, humanReviewRequired: false,
        nextStep: "Payment already settled; never retry. Confirm status with the payment provider." };
    }
    if (state === "PENDING") {
      return { category: "WAIT_AND_RECHECK", retryCandidate: false, humanReviewRequired: false,
        nextStep: "Do not retry a pending payment. Reconcile its status with the provider first." };
    }
    if (code === "DECLINED") {
      return { category: "DO_NOT_RETRY", retryCandidate: false, humanReviewRequired: false,
        nextStep: "Do not automatically retry a declined payment. Follow gateway decline guidance." };
    }
    if ((code === "TIMEOUT" || code === "RATE_LIMITED") && retries < 2) {
      return { category: "RETRY_CANDIDATE", retryCandidate: true, humanReviewRequired: false,
        nextStep: "Potential retry candidate ONLY after provider reconciliation and idempotency verification." };
    }
    return { category: "HUMAN_REVIEW", retryCandidate: false, humanReviewRequired: true,
      nextStep: "Escalate for human review; automatic retry is not recommended." };
  }

  let latest = null;
  let announcementTimer;
  function notice(text) {
    const elem = $("announcement");
    elem.textContent = text;
    elem.hidden = false;
    window.clearTimeout(announcementTimer);
    announcementTimer = window.setTimeout(() => { elem.hidden = true; }, 3500);
  }
  function applyScenario(key) {
    const preset = scenarios[key];
    if (!preset) return;
    $("payment-state").value = preset.state;
    $("gateway-code").value = preset.code;
    $("retry-count").value = preset.retries;
    $("investigation").value = preset.note;
    document.querySelectorAll(".scenario").forEach((item) => {
      const selected = item.dataset.scenario === key;
      item.classList.toggle("active", selected);
      item.setAttribute("aria-pressed", String(selected));
    });
    render();
  }
  function render() {
    const state = $("payment-state").value;
    const code = $("gateway-code").value;
    const retries = Number($("retry-count").value);
    let decision;
    try { decision = evaluate(state, code, retries); }
    catch (error) { notice(error.message); return; }
    latest = { input: { paymentState: state, gatewayCode: code, retryCount: retries }, policy: decision, explanationSource: "LOCAL_BROWSER_PREVIEW" };
    const chip = $("result-chip");
    chip.textContent = decision.category.replaceAll("_", " ");
    chip.className = "decision-chip" +
      (decision.category === "HUMAN_REVIEW" ? " warning" :
      decision.category === "DO_NOT_RETRY" ? " negative" : "");

    const headlines = {
      HUMAN_REVIEW: "A person needs to review this.",
      RESOLVED: "Already settled. Do not retry.",
      WAIT_AND_RECHECK: "Wait for provider confirmation.",
      DO_NOT_RETRY: "Automatic retry is blocked.",
      RETRY_CANDIDATE: "Reconcile before retrying."
    };
    const descriptions = {
      HUMAN_REVIEW: "The policy requires an operator to assess this synthetic incident. The system cannot automatically authorize a retry.",
      RESOLVED: "A settled payment must not be charged again. The provider state should be verified before taking any further action.",
      WAIT_AND_RECHECK: "A pending payment may still settle. Retrying before reconciliation creates a duplicate-charge risk.",
      DO_NOT_RETRY: "A declined payment is not a transient gateway failure. The provider's decline guidance determines the next step.",
      RETRY_CANDIDATE: "A timeout or rate-limit signal can be a retry candidate, but only after confirming its status with the provider and checking idempotency."
    };
    $("result-headline").textContent = headlines[decision.category];
    $("result-description").textContent = descriptions[decision.category];
    $("retry-flag").textContent = decision.retryCandidate ? "YES" : "NO";
    $("retry-flag").className = decision.retryCandidate ? "good" : "bad";
    $("review-flag").textContent = decision.humanReviewRequired ? "YES" : "NO";
    $("review-flag").className = decision.humanReviewRequired ? "bad" : "good";
    $("next-step").textContent = decision.nextStep;
    $("evaluated-at").textContent = "Simulated evaluation · " +
      new Date().toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  }

  document.querySelectorAll(".scenario").forEach((button) =>
    button.addEventListener("click", () => applyScenario(button.dataset.scenario)));
  $("scenario-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (!event.target.reportValidity()) return;
    document.querySelectorAll(".scenario").forEach((button) => {
      button.classList.remove("active");
      button.setAttribute("aria-pressed", "false");
    });
    render();
    notice("Policy evaluated locally. No data was stored.");
  });
  $("copy-result").addEventListener("click", async () => {
    if (!latest) return;
    try {
      await navigator.clipboard.writeText(JSON.stringify(latest, null, 2));
      notice("Synthetic policy result copied.");
    } catch (error) { notice("Clipboard unavailable; select the visible decision instead."); }
  });
  applyScenario("timeout");
})();