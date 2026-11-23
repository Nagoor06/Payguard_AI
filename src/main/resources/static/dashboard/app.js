"use strict";

(function () {
  const $ = (id) => document.getElementById(id);
  const state = { apiKey: "", cases: [], selectedCase: null, lastRequest: null, busy: false };
  const titles = { overview: "Overview", triage: "New incident", reviews: "Review queue", monitor: "Monitoring" };
  const allowedViews = Object.keys(titles);
  let toastTimeout = null;
  let previousFocus = null;

  function node(tag, className, value) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (value !== undefined && value !== null) element.textContent = String(value);
    return element;
  }
  function showToast(message, error) {
    const toast = $("toast");
    toast.textContent = message;
    toast.classList.toggle("error", !!error);
    toast.hidden = false;
    window.clearTimeout(toastTimeout);
    toastTimeout = window.setTimeout(() => { toast.hidden = true; }, 5600);
  }
  function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  }
  function shorten(id) { return id ? String(id).slice(0, 8) + "…" : "—"; }
  function categoryClass(category) {
    if (category === "RETRY_CANDIDATE") return "mint";
    if (category === "HUMAN_REVIEW" || category === "PENDING_REVIEW") return "amber";
    if (category === "ESCALATED") return "red";
    if (category === "ACKNOWLEDGED") return "mint";
    if (category === "NOT_REQUIRED") return "neutral";
    return "blue";
  }
  function pill(value) { return node("span", "pill " + categoryClass(value), String(value || "UNKNOWN").replaceAll("_", " ")); }
  function ensureConnected() {
    if (!state.apiKey) { throw new Error("Enter APP_API_KEY and click Connect first."); }
  }
  function reviewerKey() {
    const key = $("reviewer-key").value.trim();
    if (!key) throw new Error("Enter REVIEWER_API_KEY in the access panel to use reviewer actions.");
    return key;
  }
  async function api(path, options) {
    ensureConnected();
    const settings = options || {};
    const headers = new Headers(settings.headers || {});
    headers.set("X-API-Key", state.apiKey);
    if (settings.reviewer) headers.set("X-Reviewer-Key", reviewerKey());
    if (settings.json !== undefined) headers.set("Content-Type", "application/json");
    const response = await fetch(path, {
      method: settings.method || "GET",
      headers,
      body: settings.json === undefined ? undefined : JSON.stringify(settings.json),
      cache: "no-store",
      credentials: "same-origin"
    });
    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("json") ? await response.json() : await response.text();
    if (!response.ok) {
      let message = (payload && typeof payload === "object" && (payload.detail || payload.message)) || response.statusText;
      if (response.status === 401) message = "API key rejected. Check APP_API_KEY in your .env file.";
      if (response.status === 403) message = "Reviewer key rejected. Check REVIEWER_API_KEY in your .env file.";
      throw new Error("HTTP " + response.status + ": " + message);
    }
    return payload;
  }
  function setConnected(connected) {
    const label = $("connection-state");
    label.classList.toggle("connected", connected);
    label.textContent = connected ? "Connected. Keys are held only in this tab." : "Enter your API key to load incidents.";
    $("connect-button").textContent = connected ? "Reconnect ↗" : "Connect ↗";
  }
  function setView(view) {
    if (!allowedViews.includes(view)) return;
    allowedViews.forEach((item) => {
      $("view-" + item).hidden = item !== view;
      $("view-" + item).classList.toggle("active", item === view);
    });
    document.querySelectorAll(".nav-button").forEach((button) => {
      const current = button.dataset.view === view;
      button.classList.toggle("active", current);
      if (current) button.setAttribute("aria-current", "page");
      else button.removeAttribute("aria-current");
    });
    $("breadcrumb-current").textContent = titles[view];
    window.scrollTo({ top: 0, behavior: "smooth" });
    if (view === "monitor") { refreshHealth(); if (state.apiKey) loadMetrics(); }
    if ((view === "overview" || view === "reviews") && state.apiKey) loadRecent(true);
  }
  async function refreshHealth() {
    try {
      const response = await fetch("/actuator/health", { cache: "no-store" });
      if (!response.ok) throw new Error("HTTP " + response.status);
      const result = await response.json();
      const up = result.status === "UP";
      $("service-health").className = "health-chip " + (up ? "up" : "down");
      $("service-health").lastChild.textContent = up ? " Service online" : " Service unavailable";
      $("health-details").textContent = up ? "● Spring Boot / PostgreSQL: UP" : "Service is not healthy";
    } catch (error) {
      $("service-health").className = "health-chip down";
      $("service-health").lastChild.textContent = " Service unavailable";
      $("health-details").textContent = "Unable to reach the health endpoint.";
    }
  }
  function updateStats() {
    const cases = state.cases;
    const pending = cases.filter((item) => item.reviewStatus === "PENDING_REVIEW");
    $("stat-cases").textContent = cases.length;
    $("stat-pending").textContent = pending.length;
    $("stat-retry").textContent = cases.filter((item) => item.retryCandidate).length;
    $("stat-reviewed").textContent = cases.filter((item) => ["ACKNOWLEDGED", "ESCALATED"].includes(item.reviewStatus)).length;
    $("nav-pending").textContent = pending.length;
    $("pending-badge").textContent = pending.length + " pending";
  }
  function renderCases() {
    const query = $("case-search").value.trim().toLowerCase();
    const entries = state.cases.filter((item) => {
      return [item.id, item.paymentState, item.gatewayCode, item.category, item.reviewStatus].some((value) =>
        String(value || "").toLowerCase().includes(query));
    });
    const body = $("cases-body");
    body.replaceChildren();
    $("table-count").textContent = entries.length + " shown";
    if (!entries.length) {
      const row = node("tr");
      const cell = node("td", "empty-cell", state.apiKey ? "No incidents match this view." : "Connect to see incidents.");
      cell.colSpan = 6;
      row.appendChild(cell);
      body.appendChild(row);
      return;
    }
    entries.forEach((item) => {
      const row = node("tr");
      const idCell = node("td", "case-short", shorten(item.id));
      idCell.title = item.id;
      row.appendChild(idCell);
      row.appendChild(node("td", "", String(item.gatewayCode || "").replaceAll("_", " ")));
      const categoryCell = node("td"); categoryCell.appendChild(pill(item.category)); row.appendChild(categoryCell);
      const statusCell = node("td"); statusCell.appendChild(pill(item.reviewStatus)); row.appendChild(statusCell);
      row.appendChild(node("td", "muted", formatDate(item.createdAt)));
      const action = node("button", "table-action", "Inspect ↗");
      action.type = "button"; action.dataset.caseId = item.id;
      const actionCell = node("td"); actionCell.appendChild(action); row.appendChild(actionCell);
      body.appendChild(row);
    });
  }
  function renderReviews() {
    const list = $("review-list");
    list.replaceChildren();
    const entries = state.cases.filter((item) => item.reviewStatus === "PENDING_REVIEW");
    if (!entries.length) {
      list.appendChild(node("div", "empty-state", state.apiKey ? "No pending reviews in the latest 50 cases." : "Connect to load reviews."));
      return;
    }
    entries.forEach((item) => {
      const card = node("article", "review-item");
      const copy = node("div");
      copy.append(node("strong", "", "Case " + shorten(item.id)), node("p", "", item.gatewayCode + " · " + formatDate(item.createdAt)));
      const open = node("button", "button small outline", "Inspect & review →");
      open.type = "button"; open.dataset.caseId = item.id;
      card.append(copy, open);
      list.appendChild(card);
    });
  }
  async function loadRecent(quiet) {
    if (!state.apiKey) return;
    try {
      const cases = await api("/api/v1/triage?limit=50");
      if (!Array.isArray(cases)) throw new Error("Expected an array of incidents.");
      state.cases = cases;
      updateStats(); renderCases(); renderReviews();
      if (!quiet) showToast("Recent incidents refreshed.");
    } catch (error) {
      if (!quiet) showToast(error.message, true);
      else $("connection-state").textContent = "Could not refresh incidents: " + error.message;
    }
  }
  async function connect() {
    const key = $("api-key").value.trim();
    if (!key) { showToast("Enter APP_API_KEY from your .env file.", true); $("api-key").focus(); return; }
    state.apiKey = key;
    $("connect-button").disabled = true;
    try {
      const cases = await api("/api/v1/triage?limit=50");
      if (!Array.isArray(cases)) throw new Error("Unexpected list response.");
      state.cases = cases;
      setConnected(true); updateStats(); renderCases(); renderReviews();
      showToast("Connected to PayGuard AI.");
    } catch (error) {
      state.apiKey = "";
      setConnected(false);
      showToast(error.message, true);
    } finally {
      $("connect-button").disabled = false;
    }
  }
  function clearSession() {
    state.apiKey = ""; state.cases = []; state.selectedCase = null; state.lastRequest = null;
    $("api-key").value = ""; $("reviewer-key").value = "";
    $("replay-triage").disabled = true;
    setConnected(false); updateStats(); renderCases(); renderReviews(); closeDrawer();
    $("decision-output").replaceChildren(
      node("div", "placeholder-icon", "✦"),
      node("h3", "", "Ready for analysis"),
      node("p", "", "Create an incident to see its category, next step, and explanation source.")
    );
    $("metrics-output").textContent = "Connect to view metrics.";
    showToast("Session cleared from this browser tab.");
  }
  function renderDecision(data) {
    const target = $("decision-output"); target.className = "output-inner"; target.replaceChildren();
    const category = node("div"); category.appendChild(pill(data.policy.category)); target.appendChild(category);
    target.appendChild(node("h3", "", data.policy.retryCandidate ? "Eligible for evaluation, not auto-retry" :
      data.policy.humanReviewRequired ? "Human review required" : "Policy decision recorded"));
    const section = (title, content) => {
      const wrapper = node("div", "output-section");
      wrapper.append(node("strong", "", title), node("p", "", content));
      target.appendChild(wrapper);
    };
    section("Recommended next step", data.policy.nextStep);
    section("Explanation", data.explanation);
    section("Explanation source", data.explanationSource);
    target.appendChild(node("div", "output-case", "Case ID: " + data.caseId + " · " + data.reviewStatus));
    const actions = node("div", "output-buttons");
    const inspect = node("button", "button outline", "Open case details ↗");
    inspect.type = "button";
    inspect.dataset.caseId = data.caseId;
    actions.appendChild(inspect);
    target.appendChild(actions);
  }
  async function submitTriage(replay) {
    if (state.busy) return;
    let requestData;
    if (replay) {
      if (!state.lastRequest) return;
      requestData = state.lastRequest;
    } else {
      const retryCount = Number($("retry-count").value);
      if (!Number.isInteger(retryCount) || retryCount < 0 || retryCount > 10) {
        showToast("Retry count must be an integer between 0 and 10.", true); return;
      }
      requestData = {
        key: "ui-" + crypto.randomUUID(),
        body: { paymentState: $("payment-state").value, gatewayCode: $("gateway-code").value,
          retryCount, question: $("question").value.trim() }
      };
      if (!requestData.body.question) { showToast("Enter an investigation question.", true); return; }
    }
    try {
      state.busy = true;
      $("submit-triage").disabled = true;
      $("replay-triage").disabled = true;
      const result = await api("/api/v1/triage", {
        method: "POST", headers: { "Idempotency-Key": requestData.key }, json: requestData.body
      });
      state.lastRequest = requestData;
      renderDecision(result);
      $("replay-triage").disabled = false;
      await loadRecent(true);
      showToast(replay ? "Identical request replayed; no duplicate created." : "Synthetic incident created.");
    } catch (error) {
      showToast(error.message, true);
    } finally {
      state.busy = false;
      $("submit-triage").disabled = false;
      $("replay-triage").disabled = !state.lastRequest;
    }
  }
  function detailRow(title, content) {
    const row = node("div", "detail-row");
    row.append(node("span", "", title), node("strong", "", content == null ? "—" : content));
    return row;
  }
  function renderDrawer(data) {
    $("drawer-title").textContent = "Case " + shorten(data.id);
    const card = $("drawer-case"); card.replaceChildren();
    const head = node("div", "detail-head");
    head.append(pill(data.category), pill(data.reviewStatus));
    card.appendChild(head);
    const caseId = node("div", "detail-id", data.id);
    card.appendChild(caseId);
    card.append(detailRow("Payment state", data.paymentState), detailRow("Gateway code", data.gatewayCode),
      detailRow("Retries", data.retryCount), detailRow("Review status", data.reviewStatus),
      detailRow("Created", formatDate(data.createdAt)));
    const next = node("div", "detail-next");
    next.append(node("small", "", "Policy next step"), node("p", "", data.nextStep));
    card.appendChild(next);

    const review = $("drawer-review"); review.replaceChildren();
    if (data.reviewStatus === "PENDING_REVIEW") {
      review.appendChild(node("h3", "", "Resolve human review"));
      const form = node("form"); form.id = "review-form";
      const refLabel = node("label", "field");
      refLabel.append(node("span", "", "Reviewer reference"));
      const ref = node("input"); ref.id = "reviewer-reference"; ref.required = true;
      ref.pattern = "[A-Za-z0-9_-]{3,32}"; ref.maxLength = 32; ref.value = "ops_demo_01";
      ref.autocomplete = "off"; refLabel.appendChild(ref);
      const actionLabel = node("label", "field");
      actionLabel.append(node("span", "", "Action"));
      const select = node("select"); select.id = "review-action";
      const ack = node("option", "", "Acknowledge"); ack.value = "ACKNOWLEDGED";
      const esc = node("option", "", "Escalate"); esc.value = "ESCALATED";
      select.append(ack, esc); actionLabel.appendChild(select);
      const submit = node("button", "button primary", "Record review decision →");
      submit.type = "submit";
      form.append(refLabel, actionLabel, submit);
      review.appendChild(form);
      review.appendChild(node("p", "small-muted", "This is a one-way decision recorded in the audit trail. Reviewer key required."));
    } else {
      review.appendChild(node("p", "small-muted", "No pending reviewer action for this case."));
    }
    $("audit-events").replaceChildren(node("p", "small-muted", "Select Load secure audit to view event history."));
  }
  async function openCase(id) {
    if (!id) return;
    previousFocus = document.activeElement;
    try {
      const data = await api("/api/v1/triage/" + encodeURIComponent(id));
      state.selectedCase = data;
      renderDrawer(data);
      $("detail-overlay").hidden = false;
      $("detail-drawer").hidden = false;
      document.body.style.overflow = "hidden";
      $("close-drawer").focus();
    } catch (error) { showToast(error.message, true); }
  }
  function closeDrawer() {
    $("detail-overlay").hidden = true; $("detail-drawer").hidden = true;
    document.body.style.overflow = "";
    if (previousFocus && previousFocus.isConnected) previousFocus.focus();
    state.selectedCase = null;
  }
  async function reviewCase(event) {
    event.preventDefault();
    if (!state.selectedCase) return;
    const ref = $("reviewer-reference").value.trim();
    if (!/^[A-Za-z0-9_-]{3,32}$/.test(ref)) { showToast("Reviewer reference must be 3–32 letters, numbers, _ or -.", true); return; }
    const action = $("review-action").value;
    if (!window.confirm("Confirm " + action.toLowerCase() + " for this synthetic case? This decision cannot be undone.")) return;
    const id = state.selectedCase.id;
    try {
      const data = await api("/api/v1/triage/" + encodeURIComponent(id) + "/review",
        { method: "POST", reviewer: true, json: { reviewerRef: ref, action } });
      state.selectedCase = data; renderDrawer(data); await loadRecent(true); await loadAudit();
      showToast("Review recorded: " + action + ".");
    } catch (error) { showToast(error.message, true); }
  }
  async function loadAudit() {
    if (!state.selectedCase) return;
    try {
      const events = await api("/api/v1/triage/" + encodeURIComponent(state.selectedCase.id) + "/events",
        { reviewer: true });
      const target = $("audit-events"); target.replaceChildren();
      if (!Array.isArray(events) || !events.length) {
        target.appendChild(node("p", "small-muted", "No events available.")); return;
      }
      events.forEach((event) => {
        const item = node("div", "audit-event");
        item.append(node("strong", "", String(event.eventType || "").replaceAll("_", " ")),
          node("p", "", formatDate(event.createdAt) + (event.reviewerRef ? " · " + event.reviewerRef : "")));
        target.appendChild(item);
      });
    } catch (error) { showToast(error.message, true); }
  }
  async function loadMetrics() {
    if (!state.apiKey) { $("metrics-output").textContent = "Connect to view metrics."; return; }
    try {
      const result = await api("/actuator/prometheus");
      const filtered = String(result).split("\n").filter((line) =>
        line.startsWith("payguard_") && !line.startsWith("#"));
      $("metrics-output").textContent = filtered.length ? filtered.slice(0, 80).join("\n") :
        "No PayGuard counters yet. Create a case, then refresh metrics.";
    } catch (error) { $("metrics-output").textContent = error.message; }
  }
  document.addEventListener("click", (event) => {
    const nav = event.target.closest("[data-view]");
    if (nav) { setView(nav.dataset.view); return; }
    const caseButton = event.target.closest("[data-case-id]");
    if (caseButton) { openCase(caseButton.dataset.caseId); }
  });
  $("connect-button").addEventListener("click", connect);
  $("api-key").addEventListener("keydown", (event) => { if (event.key === "Enter") connect(); });
  $("reviewer-key").addEventListener("keydown", (event) => { if (event.key === "Enter") connect(); });
  $("clear-session").addEventListener("click", clearSession);
  $("refresh-cases").addEventListener("click", () => loadRecent(false));
  $("refresh-reviews").addEventListener("click", () => loadRecent(false));
  $("case-search").addEventListener("input", renderCases);
  $("triage-form").addEventListener("submit", (event) => { event.preventDefault(); submitTriage(false); });
  $("replay-triage").addEventListener("click", () => submitTriage(true));
  $("close-drawer").addEventListener("click", closeDrawer);
  $("detail-overlay").addEventListener("click", closeDrawer);
  $("detail-drawer").addEventListener("submit", (event) => {
    if (event.target.id === "review-form") reviewCase(event);
  });
  $("load-events").addEventListener("click", loadAudit);
  $("refresh-metrics").addEventListener("click", loadMetrics);
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !$("detail-drawer").hidden) closeDrawer();
  });
  refreshHealth();
})();