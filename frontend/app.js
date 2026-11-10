// Backend-driven demo page: every pipeline animation and result on this page comes
// from a real response or a real Server-Sent Event from the live backend - nothing
// here is scripted or faked client-side.

const SKU = "FLASH-SALE-ITEM";
// Demo convenience only (documented in README) - not a real auth boundary, matches
// the default ADMIN_SECRET baked into docker-compose.yml.
const ADMIN_SECRET = "demo-admin-secret";

const HOP_NODE_UPDATES = {
  CREATED: [["GATEWAY", "done"], ["CREATED", "done"], ["QUEUED", "active"]],
  PROCESSING: [["QUEUED", "done"], ["PROCESSING", "active"]],
  CONFIRMED: [["PROCESSING", "done"]],
  FAILED: [["PROCESSING", "failed"]],
  NOTIFIED: [["NOTIFIED", "done"]],
};

const els = {
  productName: document.getElementById("product-name"),
  productPrice: document.getElementById("product-price"),
  stockFill: document.getElementById("stock-fill"),
  stockLabel: document.getElementById("stock-label"),
  userIdInput: document.getElementById("user-id"),
  buyNowBtn: document.getElementById("buy-now-btn"),
  spikeBtn: document.getElementById("spike-btn"),
  duplicateBtn: document.getElementById("duplicate-btn"),
  resetBtn: document.getElementById("reset-btn"),
  orderStatus: document.getElementById("order-status"),
  notificationPreview: document.getElementById("notification-preview"),
  eventLogContent: document.getElementById("event-log-content"),
  resultsContent: document.getElementById("results-content"),
};

let maxObservedStock = 0;
let currentEventSource = null;

function uuid() {
  return crypto.randomUUID();
}

async function fetchProductRaw() {
  try {
    const res = await fetch(`/api/products/${SKU}`);
    if (!res.ok) return null;
    return await res.json();
  } catch (e) {
    return null;
  }
}

async function refreshProduct() {
  const data = await fetchProductRaw();
  if (!data) {
    els.productName.textContent = "Backend unreachable";
    return;
  }
  els.productName.textContent = data.name;
  els.productPrice.textContent = `$${(data.priceCents / 100).toFixed(2)}`;
  maxObservedStock = Math.max(maxObservedStock, data.remainingStock);
  const pct = maxObservedStock > 0 ? Math.max(0, Math.min(100, (data.remainingStock / maxObservedStock) * 100)) : 0;
  els.stockFill.style.width = `${pct}%`;
  els.stockLabel.textContent = `${data.remainingStock} / ${maxObservedStock} remaining`;
}

async function checkout(idempotencyKey, userId) {
  try {
    const res = await fetch("/api/checkout", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ sku: SKU, userId, quantity: 1 }),
    });
    let body = null;
    try { body = await res.json(); } catch (e) { /* no body */ }
    return { status: res.status, body };
  } catch (e) {
    return { status: 0, body: null };
  }
}

function setNodeState(hop, state) {
  const node = document.querySelector(`.node[data-hop="${hop}"]`);
  if (!node) return;
  node.classList.remove("active", "done", "failed");
  node.classList.add(state);
  const edge = node.previousElementSibling;
  if (edge && edge.classList.contains("edge")) {
    edge.classList.add("done");
  }
}

function resetPipelineUI() {
  document.querySelectorAll(".node").forEach((n) => n.classList.remove("active", "done", "failed"));
  document.querySelectorAll(".edge").forEach((e) => e.classList.remove("done"));
  els.orderStatus.textContent = "";
  els.orderStatus.className = "order-status";
  els.notificationPreview.hidden = true;
  els.eventLogContent.textContent = "";
}

function setOrderStatus(text, kind) {
  els.orderStatus.textContent = text;
  els.orderStatus.className = `order-status ${kind || ""}`;
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str == null ? "" : str;
  return div.innerHTML;
}

function logEvent(event) {
  els.eventLogContent.textContent += JSON.stringify(event) + "\n";
  els.eventLogContent.scrollTop = els.eventLogContent.scrollHeight;
}

function showNotificationPreview(event) {
  els.notificationPreview.hidden = false;
  els.notificationPreview.innerHTML = `
    <div class="subject">${escapeHtml(event.notificationSubject)}</div>
    <div class="body">${escapeHtml(event.notificationBody)}</div>
    <div class="meta">Rendered by notification-service &middot; no real email sent unless you opt in</div>
  `;
}

function applyHop(event) {
  const updates = HOP_NODE_UPDATES[event.hop] || [];
  updates.forEach(([hop, state]) => setNodeState(hop, state));
  if (event.hop === "CONFIRMED") setOrderStatus("Order confirmed.", "ok");
  if (event.hop === "FAILED") setOrderStatus(`Order failed: ${event.failureReason || "unknown reason"}`, "err");
}

function openTrackingStream(idempotencyKey) {
  if (currentEventSource) {
    currentEventSource.close();
  }
  const es = new EventSource(`/api/tracking/${idempotencyKey}/events`);
  currentEventSource = es;
  es.onmessage = (ev) => {
    let event;
    try { event = JSON.parse(ev.data); } catch (e) { return; }
    logEvent(event);
    applyHop(event);
    if (event.hop === "NOTIFIED") {
      showNotificationPreview(event);
      es.close();
    }
  };
  es.onerror = () => {
    es.close();
  };
}

async function onBuyNow() {
  resetPipelineUI();
  const idempotencyKey = uuid();
  const userId = els.userIdInput.value.trim() || "demo-user";
  setNodeState("GATEWAY", "active");
  openTrackingStream(idempotencyKey);

  const result = await checkout(idempotencyKey, userId);
  if (result.status === 409) {
    setOrderStatus("Sold out.", "err");
  } else if (result.status === 0) {
    setOrderStatus("Could not reach the backend.", "err");
  } else if (result.status >= 400) {
    setOrderStatus(`Request rejected: ${result.body?.error || result.status}`, "err");
  }
  refreshProduct();
}

function renderResults(stats) {
  const grid = stats
    .map(
      (s) => `
      <div class="result-stat ${s.cls || ""}">
        <div class="value">${s.value}</div>
        <div class="label">${s.label}</div>
      </div>`
    )
    .join("");
  els.resultsContent.innerHTML = `<div class="results-grid">${grid}</div>`;
}

function setResultsLoading(text) {
  els.resultsContent.innerHTML = `<p class="hint">${escapeHtml(text)}</p>`;
}

function setButtonsDisabled(disabled) {
  [els.buyNowBtn, els.spikeBtn, els.duplicateBtn, els.resetBtn].forEach((b) => (b.disabled = disabled));
}

async function onSpike() {
  setButtonsDisabled(true);
  setResultsLoading("Firing 200 concurrent checkout requests from randomized fake users...");
  const requests = Array.from({ length: 200 }, (_, i) => checkout(uuid(), `spike-user-${i}`));
  const settled = await Promise.allSettled(requests);
  const results = settled.map((r) => (r.status === "fulfilled" ? r.value : { status: 0, body: null }));
  const accepted = results.filter((r) => r.status === 202).length;
  const soldOut = results.filter((r) => r.status === 409).length;
  const other = results.length - accepted - soldOut;
  const product = await fetchProductRaw();

  renderResults([
    { label: "Requests sent", value: results.length },
    { label: "Accepted", value: accepted, cls: "success" },
    { label: "Sold out (rejected)", value: soldOut, cls: "danger" },
    { label: "Other", value: other },
    { label: "Stock remaining", value: product ? product.remainingStock : "?" },
  ]);
  await refreshProduct();
  setButtonsDisabled(false);
}

async function onDuplicate() {
  setButtonsDisabled(true);
  setResultsLoading("Firing 50 concurrent requests that all share one Idempotency-Key...");
  const sharedKey = uuid();
  const requests = Array.from({ length: 50 }, () => checkout(sharedKey, "duplicate-user"));
  const settled = await Promise.allSettled(requests);
  const results = settled.map((r) => (r.status === "fulfilled" ? r.value : { status: 0, body: null }));
  const realOrders = results.filter((r) => r.status === 202).length;
  const duplicatesIgnored = results.filter((r) => r.status === 200 && r.body?.status === "DUPLICATE_IGNORED").length;

  renderResults([
    { label: "Requests sent", value: results.length },
    { label: "Real order created", value: realOrders, cls: realOrders === 1 ? "success" : "danger" },
    { label: "Duplicate ignored", value: duplicatesIgnored },
  ]);
  await refreshProduct();
  setButtonsDisabled(false);
}

async function onReset() {
  setButtonsDisabled(true);
  try {
    await fetch("/api/admin/reset", { method: "POST", headers: { "X-Admin-Secret": ADMIN_SECRET } });
  } catch (e) {
    /* ignore - best-effort demo convenience */
  }
  maxObservedStock = 0;
  resetPipelineUI();
  els.resultsContent.innerHTML = '<p class="hint">Run a simulation above to see aggregate results here.</p>';
  await refreshProduct();
  setButtonsDisabled(false);
}

els.buyNowBtn.addEventListener("click", onBuyNow);
els.spikeBtn.addEventListener("click", onSpike);
els.duplicateBtn.addEventListener("click", onDuplicate);
els.resetBtn.addEventListener("click", onReset);

refreshProduct();
setInterval(refreshProduct, 1500);
