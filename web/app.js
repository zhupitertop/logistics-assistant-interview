"use strict";

(() => {
  const form = document.getElementById("question-form");
  const question = document.getElementById("question");
  const submit = document.getElementById("submit-button");
  const errorMessage = document.getElementById("error-message");
  const loadingStatus = document.getElementById("loading-status");
  const result = document.getElementById("result");
  const examples = Array.from(document.querySelectorAll("[data-question]"));
  const statuses = { answered: "已回答", order_not_found: "未找到订单", insufficient_evidence: "依据不足" };
  const tools = { order_query: "查询订单", sop_search: "检索 SOP", generate: "生成回答" };
  const outcomes = { ok: "完成", not_found: "未找到", no_hit: "无有效命中" };
  let busy = false;
  let requestId = 0;

  class DisplayError extends Error {}

  function make(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
  }

  function showError(message) {
    errorMessage.textContent = message;
    errorMessage.hidden = false;
    errorMessage.focus();
  }

  function setBusy(value) {
    busy = value;
    submit.disabled = value;
    question.disabled = value;
    examples.forEach(button => { button.disabled = value; });
    form.setAttribute("aria-busy", String(value));
    submit.textContent = value ? "查询中…" : "查询";
    loadingStatus.textContent = value ? "正在查询，请稍候。" : "";
  }

  function updateCount() {
    const length = Array.from(question.value).length;
    const count = document.getElementById("question-count");
    count.textContent = `${length} / 2000 字`;
    count.classList.toggle("over-limit", length > 2000);
    question.removeAttribute("aria-invalid");
    errorMessage.hidden = true;
  }

  const strings = value => Array.isArray(value) && value.every(item => typeof item === "string");
  const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
  const nonempty = value => typeof value === "string" && value.trim().length > 0;

  function validateResponse(payload) {
    const invalid = () => { throw new DisplayError("服务返回的数据格式不正确，请重试。"); };
    if (!object(payload) || typeof payload.status !== "string" || !Object.hasOwn(statuses, payload.status) || !nonempty(payload.answer)
        || !Array.isArray(payload.citations) || !Array.isArray(payload.trace) || payload.trace.length === 0) invalid();
    for (let i = 0; i < payload.trace.length; i++) {
      const step = payload.trace[i];
      if (!object(step) || step.seq !== i + 1 || typeof step.tool !== "string" || !Object.hasOwn(tools, step.tool)
          || typeof step.outcome !== "string" || !Object.hasOwn(outcomes, step.outcome) || !strings(step.hit_ids) || !object(step.input)
          || !Object.values(step.input).every(value => typeof value === "string" || strings(value))) invalid();
    }
    const generated = payload.trace[payload.trace.length - 1];
    if (generated.tool !== "generate" || payload.trace.filter(step => step.tool === "generate").length !== 1
        || !strings(generated.input.evidence_ids)
        || JSON.stringify(generated.input.evidence_ids) !== JSON.stringify(generated.hit_ids)) invalid();
    for (const citation of payload.citations) {
      if (!object(citation) || !nonempty(citation.id) || !nonempty(citation.title) || typeof citation.excerpt !== "string" || citation.excerpt.length === 0
          || Array.from(citation.excerpt).length > 120
          || !payload.trace.some(step => step.tool === "sop_search" && step.outcome === "ok" && step.hit_ids.includes(citation.id))
          || !generated.hit_ids.includes(citation.id)) invalid();
    }
    const citedIds = Array.from(new Set(payload.citations.map(citation => citation.id)));
    if (JSON.stringify(citedIds) !== JSON.stringify(generated.hit_ids)) invalid();
  }

  function renderTrace(trace) {
    const steps = document.getElementById("trace-steps");
    steps.replaceChildren();
    document.getElementById("trace-details").open = false;
    document.getElementById("trace-count").textContent = `${trace.length} 步`;
    for (const step of trace) {
      const details = make("details", "trace-step");
      details.id = `trace-step-${step.seq}`;
      details.append(make("summary", "", `步骤 ${step.seq} · ${tools[step.tool]} · ${outcomes[step.outcome]}`));
      details.append(make("p", "muted", `工具：${step.tool}　结果：${step.outcome}`));
      details.append(make("p", "trace-label", "实际输入"));
      details.append(make("pre", "trace-input", JSON.stringify(step.input, null, 2)));
      details.append(make("p", "trace-hits", `SOP 命中编号：${step.hit_ids.length ? step.hit_ids.join("、") : "无"}`));
      steps.append(details);
    }
  }

  function renderCitations(citations, trace) {
    const list = document.getElementById("citations");
    list.replaceChildren();
    document.getElementById("citation-count").textContent = `${citations.length} 条`;
    document.getElementById("no-citations").hidden = citations.length !== 0;
    for (const [index, citation] of citations.entries()) {
      const item = make("li", "citation");
      const header = make("div", "citation-header");
      header.append(make("h3", "", `[${index + 1}] ${citation.title}`));
      header.append(make("code", "citation-id", citation.id));
      item.append(header, make("blockquote", "excerpt", citation.excerpt));
      const step = trace.find(entry => entry.tool === "sop_search" && entry.outcome === "ok" && entry.hit_ids.includes(citation.id));
      const link = make("a", "citation-link", `核对检索步骤 ${step.seq}`);
      link.href = `#trace-step-${step.seq}`;
      link.addEventListener("click", () => {
        document.getElementById("trace-details").open = true;
        document.getElementById(`trace-step-${step.seq}`).open = true;
      });
      item.append(link);
      list.append(item);
    }
  }

  function render(payload, submittedQuestion) {
    document.getElementById("answered-question").textContent = submittedQuestion;
    document.getElementById("answer").textContent = payload.answer;
    const badge = document.getElementById("business-status");
    badge.textContent = statuses[payload.status];
    badge.dataset.status = payload.status;
    renderTrace(payload.trace);
    renderCitations(payload.citations, payload.trace);
    result.hidden = false;
    document.getElementById("result-heading").focus();
  }

  question.addEventListener("input", updateCount);
  question.addEventListener("keydown", event => {
    if (event.key === "Enter" && (event.ctrlKey || event.metaKey) && !event.isComposing) {
      event.preventDefault();
      form.requestSubmit();
    }
  });
  examples.forEach(button => button.addEventListener("click", () => {
    if (busy) return;
    question.value = button.dataset.question;
    updateCount();
    question.focus();
  }));

  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (busy) return;
    errorMessage.hidden = true;
    const submittedQuestion = question.value;
    if (!submittedQuestion.trim() || Array.from(submittedQuestion).length > 2000) {
      question.setAttribute("aria-invalid", "true");
      showError("请输入 1 至 2000 字的非空问题。");
      return;
    }
    result.hidden = true;
    const current = ++requestId;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 30000);
    setBusy(true);
    try {
      const response = await fetch("/ask", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ question: submittedQuestion }),
        signal: controller.signal
      });
      let payload;
      try { payload = await response.json(); }
      catch (error) {
        if (controller.signal.aborted) throw error;
        throw new DisplayError(`服务响应不是有效 JSON（HTTP ${response.status}），请重试。`);
      }
      if (controller.signal.aborted) throw new DisplayError("查询超时，请稍后重试。");
      if (current !== requestId) return;
      if (!response.ok) {
        const detail = payload && payload.error && typeof payload.error.message === "string" ? `：${payload.error.message}` : "，请稍后重试。";
        throw new DisplayError(`请求失败（HTTP ${response.status}）${detail}`);
      }
      validateResponse(payload);
      render(payload, submittedQuestion);
    } catch (error) {
      if (current !== requestId) return;
      const message = controller.signal.aborted ? "查询超时，请稍后重试。"
        : error instanceof DisplayError ? error.message : "无法连接服务。请检查服务是否运行，然后重试。";
      showError(message);
    } finally {
      window.clearTimeout(timeout);
      if (current === requestId) setBusy(false);
    }
  });
  updateCount();
  setBusy(false);
})();
