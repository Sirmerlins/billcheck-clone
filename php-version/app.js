(function () {
  'use strict';

  const TOTAL_PEOPLE = 146;
  const initialPaid = Array.isArray(window.__INITIAL_PAID__) ? window.__INITIAL_PAID__ : [];

  let paidNumbers = sanitizeList(initialPaid);
  let history = [];
  let filterMode = 'all';
  let lastCheckedNumbers = [];
  let feedback = null;
  let confirmReset = false;
  let lastScrolledFocus = null;

  const inputEl = document.getElementById('person-number-input');
  const clearBtn = document.getElementById('clear-input-btn');
  const formEl = document.getElementById('lookup-form');
  const feedbackEl = document.getElementById('feedback-line');
  const paidCountEl = document.getElementById('paid-count');
  const remainingCountEl = document.getElementById('remaining-count');
  const progressBarEl = document.getElementById('progress-bar');
  const progressFillEl = document.getElementById('progress-fill');
  const collectedTextEl = document.getElementById('collected-text');
  const gridEl = document.getElementById('checklist-grid');
  const emptyStateEl = document.getElementById('empty-state');
  const emptyTitleEl = document.getElementById('empty-title');
  const emptyDescEl = document.getElementById('empty-desc');
  const emptyResetBtn = document.getElementById('empty-reset-btn');
  const undoBtn = document.getElementById('undo-btn');
  const copyUnpaidBtn = document.getElementById('copy-unpaid-btn');
  const resetBtn = document.getElementById('reset-btn');
  const resetConfirmGroup = document.getElementById('reset-confirm-group');
  const confirmResetBtn = document.getElementById('confirm-reset-btn');
  const cancelResetBtn = document.getElementById('cancel-reset-btn');
  const filterTabs = Array.from(document.querySelectorAll('.filter-tab'));

  function sanitizeList(arr) {
    const set = new Set();
    for (const n of arr) {
      const num = Number(n);
      if (Number.isInteger(num) && num >= 1 && num <= TOTAL_PEOPLE) {
        set.add(num);
      }
    }
    return Array.from(set).sort((a, b) => a - b);
  }

  function parseNumberInput(raw) {
    const trimmed = raw.trim();
    if (!trimmed) {
      return { validNumbers: [], outOfRange: [], invalidTokens: [] };
    }
    const tokens = trimmed.split(/[\s,;]+/).filter(Boolean);
    const validSet = new Set();
    const outOfRangeSet = new Set();
    const invalidTokens = [];

    for (const rawToken of tokens) {
      const token = rawToken.replace(/^#/, '');
      const rangeMatch = token.match(/^(\d+)(?:-|\.\.)(\d+)$/);
      if (rangeMatch) {
        const start = Number(rangeMatch[1]);
        const end = Number(rangeMatch[2]);
        const low = Math.min(start, end);
        const high = Math.max(start, end);
        if (high - low > 1000 && (low < 1 || high > TOTAL_PEOPLE)) {
          invalidTokens.push(rawToken);
          continue;
        }
        for (let n = low; n <= high; n++) {
          if (n >= 1 && n <= TOTAL_PEOPLE) validSet.add(n);
          else outOfRangeSet.add(n);
        }
        continue;
      }

      if (/^\d+$/.test(token)) {
        const num = Number(token);
        if (num >= 1 && num <= TOTAL_PEOPLE) validSet.add(num);
        else outOfRangeSet.add(num);
        continue;
      }

      invalidTokens.push(rawToken);
    }

    return {
      validNumbers: Array.from(validSet),
      outOfRange: Array.from(outOfRangeSet),
      invalidTokens: invalidTokens,
    };
  }

  async function syncServer(payload) {
    try {
      const res = await fetch('api.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (!res.ok) {
        feedback = {
          tone: 'error',
          message: data && data.error ? data.error : 'Server failed to save changes.',
        };
        render();
        return false;
      }
      if (Array.isArray(data.paid)) {
        paidNumbers = sanitizeList(data.paid);
      }
      return true;
    } catch (err) {
      feedback = {
        tone: 'error',
        message: 'Network error while saving to server.',
      };
      render();
      return false;
    }
  }

  function pushHistory(snapshot) {
    history = history.slice(-19).concat([snapshot]);
  }

  async function markNumbersPaid(nums) {
    const prev = paidNumbers.slice();
    const nextSet = new Set(paidNumbers);
    const newlyMarked = [];
    for (const n of nums) {
      if (!nextSet.has(n)) {
        nextSet.add(n);
        newlyMarked.push(n);
      }
    }
    if (newlyMarked.length > 0) {
      pushHistory(prev);
      paidNumbers = Array.from(nextSet).sort((a, b) => a - b);
      render();
      await syncServer({ action: 'mark', numbers: newlyMarked });
      render();
    }
    return newlyMarked;
  }

  async function unmarkNumbersPaid(nums) {
    const prev = paidNumbers.slice();
    const removeSet = new Set(nums);
    const next = paidNumbers.filter((n) => !removeSet.has(n));
    if (next.length !== paidNumbers.length) {
      pushHistory(prev);
      paidNumbers = next;
      render();
      await syncServer({ action: 'unmark', numbers: nums });
      render();
    }
  }

  async function handleToggleSingle(num) {
    const paidSet = new Set(paidNumbers);
    const wasPaid = paidSet.has(num);
    confirmReset = false;
    lastCheckedNumbers = [num];

    if (wasPaid) {
      await unmarkNumbersPaid([num]);
      feedback = {
        tone: 'neutral',
        message: 'Marked #' + num + ' as unpaid.',
        actionLabel: 'Undo',
        onAction: handleUndo,
      };
    } else {
      await markNumbersPaid([num]);
      feedback = {
        tone: 'success',
        message: 'Marked #' + num + ' as paid.',
        actionLabel: 'Undo',
        onAction: handleUndo,
      };
    }
    render();
  }

  async function handleUndo() {
    if (history.length === 0) return;
    const previous = history.pop();
    paidNumbers = previous.slice();
    feedback = { tone: 'neutral', message: 'Reverted the last change.' };
    render();
    await syncServer({ action: 'set', numbers: paidNumbers });
    render();
  }

  async function handleConfirmReset() {
    if (paidNumbers.length === 0) {
      confirmReset = false;
      render();
      return;
    }
    pushHistory(paidNumbers.slice());
    paidNumbers = [];
    lastCheckedNumbers = [];
    confirmReset = false;
    feedback = {
      tone: 'neutral',
      message: 'Cleared all 146 payment checkmarks.',
      actionLabel: 'Undo',
      onAction: handleUndo,
    };
    render();
    await syncServer({ action: 'reset' });
    render();
  }

  function renderFeedback(parsedLive, paidSet) {
    const raw = inputEl.value;
    feedbackEl.innerHTML = '';

    if (raw.trim()) {
      if (parsedLive.validNumbers.length === 0) {
        const span = document.createElement('span');
        span.className = 'text-error';
        if (parsedLive.outOfRange.length > 0) {
          span.textContent =
            'Out of range (' +
            parsedLive.outOfRange.join(', ') +
            ') — enter 1 to ' +
            TOTAL_PEOPLE +
            '.';
        } else {
          span.textContent =
            'Enter numbers from 1 to ' + TOTAL_PEOPLE + ' (e.g. 42 or 12, 15, 20-25).';
        }
        feedbackEl.appendChild(span);
        return;
      }

      if (parsedLive.validNumbers.length === 1) {
        const num = parsedLive.validNumbers[0];
        const isPaid = paidSet.has(num);
        const span = document.createElement('span');
        span.className = isPaid ? 'text-success' : 'text-info';
        span.textContent = isPaid
          ? '#' + num + ' has already paid'
          : '#' + num + ' has not paid yet — press Enter to mark paid';
        feedbackEl.appendChild(span);

        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'inline-action-link';
        btn.textContent = isPaid ? 'Unmark #' + num : 'Mark #' + num + ' Paid';
        btn.addEventListener('click', function () {
          handleToggleSingle(num);
          inputEl.value = '';
          inputEl.focus();
          render();
        });
        feedbackEl.appendChild(btn);
        return;
      }

      const alreadyPaid = parsedLive.validNumbers.filter((n) => paidSet.has(n)).length;
      const unpaid = parsedLive.validNumbers.length - alreadyPaid;
      const span = document.createElement('span');
      span.className = unpaid === 0 ? 'text-success' : 'text-info';
      span.textContent =
        parsedLive.validNumbers.length +
        ' numbers selected (' +
        unpaid +
        ' unpaid, ' +
        alreadyPaid +
        ' already paid) — press Enter to mark paid';
      feedbackEl.appendChild(span);
      return;
    }

    if (feedback) {
      const span = document.createElement('span');
      span.className =
        feedback.tone === 'error'
          ? 'text-error'
          : feedback.tone === 'success'
            ? 'text-success'
            : 'text-neutral';
      span.textContent = feedback.message;
      feedbackEl.appendChild(span);

      if (feedback.actionLabel && typeof feedback.onAction === 'function') {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'inline-action-link';
        btn.textContent = feedback.actionLabel;
        btn.addEventListener('click', feedback.onAction);
        feedbackEl.appendChild(btn);
      }
      return;
    }

    const hint = document.createElement('span');
    hint.className = 'text-muted';
    hint.textContent = 'Typing highlights the number in the grid below. Press Enter to check it off.';
    feedbackEl.appendChild(hint);
  }

  function render() {
    const paidSet = new Set(paidNumbers);
    const parsedLive = parseNumberInput(inputEl.value);
    const highlighted =
      parsedLive.validNumbers.length > 0 ? parsedLive.validNumbers : lastCheckedNumbers;
    const highlightedSet = new Set(highlighted);
    const primaryFocus = highlighted.length > 0 ? highlighted[highlighted.length - 1] : null;

    clearBtn.classList.toggle('hidden', inputEl.value.length === 0);

    const paidCount = paidNumbers.length;
    const unpaidCount = TOTAL_PEOPLE - paidCount;
    const paidPercent = Math.round((paidCount / TOTAL_PEOPLE) * 1000) / 10;

    paidCountEl.textContent = String(paidCount);
    remainingCountEl.textContent = String(unpaidCount);
    progressFillEl.style.width = paidPercent + '%';
    progressBarEl.setAttribute('aria-valuenow', String(paidCount));
    progressBarEl.setAttribute('aria-label', paidCount + ' of ' + TOTAL_PEOPLE + ' people paid');
    collectedTextEl.textContent = paidPercent + '% collected';

    undoBtn.disabled = history.length === 0;
    resetBtn.disabled = paidCount === 0;

    if (confirmReset) {
      resetBtn.classList.add('hidden');
      resetConfirmGroup.classList.remove('hidden');
    } else {
      resetBtn.classList.remove('hidden');
      resetConfirmGroup.classList.add('hidden');
    }

    filterTabs.forEach((tab) => {
      const mode = tab.getAttribute('data-filter');
      tab.classList.toggle('active', mode === filterMode);
      if (mode === 'all') tab.textContent = 'All (' + TOTAL_PEOPLE + ')';
      if (mode === 'paid') tab.textContent = 'Paid (' + paidCount + ')';
      if (mode === 'unpaid') tab.textContent = 'Unpaid (' + unpaidCount + ')';
    });

    renderFeedback(parsedLive, paidSet);

    // Render Grid
    const visibleNumbers = [];
    for (let n = 1; n <= TOTAL_PEOPLE; n++) {
      const isPaid = paidSet.has(n);
      if (filterMode === 'paid' && !isPaid) continue;
      if (filterMode === 'unpaid' && isPaid) continue;
      visibleNumbers.push(n);
    }

    gridEl.innerHTML = '';

    if (visibleNumbers.length === 0) {
      gridEl.classList.add('hidden');
      emptyStateEl.classList.remove('hidden');
      emptyTitleEl.textContent =
        filterMode === 'paid' ? 'No one has been marked paid yet' : 'Everyone has paid';
      emptyDescEl.textContent =
        filterMode === 'paid'
          ? 'Enter a number above or click any person in the full checklist to mark them as paid.'
          : 'All 146 people are currently marked as paid.';
      return;
    }

    gridEl.classList.remove('hidden');
    emptyStateEl.classList.add('hidden');

    let focusButton = null;

    for (const num of visibleNumbers) {
      const isPaid = paidSet.has(num);
      const isHighlighted = highlightedSet.has(num);

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'grid-cell' + (isPaid ? ' is-paid' : '') + (isHighlighted ? ' is-highlighted' : '');
      btn.setAttribute('aria-pressed', isPaid ? 'true' : 'false');
      btn.setAttribute(
        'aria-label',
        'Number ' + num + ', ' + (isPaid ? 'paid' : 'unpaid') + (isHighlighted ? ', currently selected' : '')
      );

      const label = document.createElement('span');
      label.textContent = String(num);
      btn.appendChild(label);

      if (isPaid) {
        const iconWrap = document.createElement('span');
        iconWrap.className = 'cell-check';
        iconWrap.setAttribute('aria-hidden', 'true');
        iconWrap.innerHTML =
          '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';
        btn.appendChild(iconWrap);
      } else {
        const box = document.createElement('span');
        box.className = 'cell-box';
        box.setAttribute('aria-hidden', 'true');
        btn.appendChild(box);
      }

      btn.addEventListener('click', function () {
        handleToggleSingle(num);
      });

      gridEl.appendChild(btn);

      if (num === primaryFocus) {
        focusButton = btn;
      }
    }

    if (focusButton && primaryFocus !== lastScrolledFocus) {
      lastScrolledFocus = primaryFocus;
      focusButton.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } else if (primaryFocus === null) {
      lastScrolledFocus = null;
    }
  }

  inputEl.addEventListener('input', function () {
    if (feedback) feedback = null;
    render();
  });

  clearBtn.addEventListener('click', function () {
    inputEl.value = '';
    inputEl.focus();
    render();
  });

  formEl.addEventListener('submit', async function (e) {
    e.preventDefault();
    confirmReset = false;

    const raw = inputEl.value.trim();
    if (!raw) {
      feedback = { tone: 'error', message: 'Enter a number between 1 and 146.' };
      inputEl.focus();
      render();
      return;
    }

    const parsed = parseNumberInput(raw);
    if (parsed.validNumbers.length === 0) {
      if (parsed.outOfRange.length > 0) {
        feedback = {
          tone: 'error',
          message:
            'Out of range (' +
            parsed.outOfRange.join(', ') +
            '). Enter numbers from 1 to ' +
            TOTAL_PEOPLE +
            '.',
        };
      } else {
        feedback = {
          tone: 'error',
          message:
            'Invalid input (' +
            parsed.invalidTokens.join(', ') +
            '). Use numbers from 1 to ' +
            TOTAL_PEOPLE +
            '.',
        };
      }
      render();
      return;
    }

    const newlyMarked = await markNumbersPaid(parsed.validNumbers);
    lastCheckedNumbers = parsed.validNumbers.slice();
    inputEl.value = '';
    inputEl.focus();

    if (newlyMarked.length === 0) {
      if (parsed.validNumbers.length === 1) {
        const target = parsed.validNumbers[0];
        feedback = {
          tone: 'neutral',
          message: '#' + target + ' is already marked paid.',
          actionLabel: 'Unmark #' + target,
          onAction: async function () {
            await unmarkNumbersPaid([target]);
            feedback = { tone: 'neutral', message: 'Marked #' + target + ' as unpaid.' };
            render();
          },
        };
      } else {
        feedback = {
          tone: 'neutral',
          message: 'All ' + parsed.validNumbers.length + ' entered numbers were already paid.',
          actionLabel: 'Unmark all',
          onAction: async function () {
            await unmarkNumbersPaid(parsed.validNumbers);
            feedback = {
              tone: 'neutral',
              message: 'Marked ' + parsed.validNumbers.length + ' numbers as unpaid.',
            };
            render();
          },
        };
      }
      render();
      return;
    }

    const previewList =
      newlyMarked.length <= 5
        ? newlyMarked.map((n) => '#' + n).join(', ')
        : newlyMarked.length + ' numbers';

    const warningSuffix =
      parsed.outOfRange.length > 0
        ? ' (skipped out-of-range: ' + parsed.outOfRange.join(', ') + ')'
        : '';

    feedback = {
      tone: 'success',
      message: 'Marked ' + previewList + ' as paid.' + warningSuffix,
      actionLabel: 'Undo',
      onAction: async function () {
        await unmarkNumbersPaid(newlyMarked);
        feedback = { tone: 'neutral', message: 'Reverted ' + previewList + '.' };
        render();
      },
    };
    render();
  });

  filterTabs.forEach((tab) => {
    tab.addEventListener('click', function () {
      filterMode = tab.getAttribute('data-filter') || 'all';
      render();
    });
  });

  emptyResetBtn.addEventListener('click', function () {
    filterMode = 'all';
    render();
  });

  undoBtn.addEventListener('click', handleUndo);

  copyUnpaidBtn.addEventListener('click', async function () {
    const paidSet = new Set(paidNumbers);
    const unpaid = [];
    for (let n = 1; n <= TOTAL_PEOPLE; n++) {
      if (!paidSet.has(n)) unpaid.push(n);
    }
    const text = unpaid.length > 0 ? unpaid.join(', ') : 'Everyone has paid!';
    try {
      await navigator.clipboard.writeText(text);
      const originalHtml = copyUnpaidBtn.innerHTML;
      copyUnpaidBtn.textContent = 'Copied Unpaid';
      setTimeout(function () {
        copyUnpaidBtn.innerHTML = originalHtml;
      }, 1800);
    } catch {
      feedback = { tone: 'error', message: 'Could not access clipboard in this browser.' };
      render();
    }
  });

  resetBtn.addEventListener('click', function () {
    confirmReset = true;
    render();
  });

  confirmResetBtn.addEventListener('click', handleConfirmReset);

  cancelResetBtn.addEventListener('click', function () {
    confirmReset = false;
    render();
  });

  render();
})();
