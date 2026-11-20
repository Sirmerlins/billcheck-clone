<?php
declare(strict_types=1);

const TOTAL_PEOPLE = 146;
$dataFile = __DIR__ . '/data/paid.json';
$initialPaid = [];

if (is_readable($dataFile)) {
    $raw = @file_get_contents($dataFile);
    $decoded = json_decode($raw ?: '', true);
    if (is_array($decoded) && isset($decoded['paid']) && is_array($decoded['paid'])) {
        foreach ($decoded['paid'] as $n) {
            if (is_numeric($n)) {
                $num = (int)$n;
                if ($num >= 1 && $num <= TOTAL_PEOPLE) {
                    $initialPaid[$num] = $num;
                }
            }
        }
        ksort($initialPaid, SORT_NUMERIC);
        $initialPaid = array_values($initialPaid);
    }
}
?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>BillPay 146 — Payment Checklist</title>
  <meta name="description" content="Fast number-lookup and 146-person grid checklist to track and verify who has paid the bill." />
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link
    href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap"
    rel="stylesheet"
  />
  <link rel="stylesheet" href="styles.css" />
</head>
<body>
  <header class="topbar">
    <div class="container topbar-inner">
      <a href="./" class="brand">BillPay 146</a>
      <div class="topbar-actions">
        <a href="api.php?action=csv" class="btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="7 10 12 15 17 10"></polyline>
            <line x1="12" y1="15" x2="12" y2="3"></line>
          </svg>
          Export CSV
        </a>

        <button type="button" id="reset-btn" class="btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path>
            <path d="M3 3v5h5"></path>
          </svg>
          Reset
        </button>

        <div id="reset-confirm-group" class="topbar-actions hidden">
          <button type="button" id="confirm-reset-btn" class="btn btn-danger">Confirm Reset</button>
          <button type="button" id="cancel-reset-btn" class="btn btn-ghost">Cancel</button>
        </div>
      </div>
    </div>
  </header>

  <main class="container main-content">
    <section class="summary-panel" aria-label="Number lookup and payment summary">
      <div class="summary-grid">
        <div class="input-section">
          <div class="input-header">
            <label for="person-number-input" class="input-label">Check or mark number</label>
            <span class="range-hint">Range: 1–146</span>
          </div>

          <form id="lookup-form" class="input-form">
            <div class="input-wrap">
              <span class="input-icon" aria-hidden="true">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="11" cy="11" r="8"></circle>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                </svg>
              </span>
              <input
                id="person-number-input"
                type="text"
                class="number-input"
                placeholder="Type number (e.g. 42) or list (12, 18, 25-30)"
                autocomplete="off"
                autofocus
              />
              <button
                type="button"
                id="clear-input-btn"
                class="clear-input-btn hidden"
                aria-label="Clear number input"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </button>
            </div>

            <button type="submit" class="btn btn-primary">Mark Paid</button>
          </form>

          <div id="feedback-line" class="feedback-line" aria-live="polite"></div>
        </div>

        <div class="tally-section">
          <div class="tally-numbers">
            <div>
              <span class="tally-label">Paid</span>
              <div class="tally-value-row">
                <span id="paid-count" class="tally-big">0</span>
                <span class="tally-total">/ 146</span>
              </div>
            </div>
            <div style="text-align: right;">
              <span class="tally-label">Remaining</span>
              <div id="remaining-count" class="tally-big" style="margin-top: 0.125rem;">146</div>
            </div>
          </div>

          <div
            id="progress-bar"
            class="progress-track"
            role="progressbar"
            aria-valuenow="0"
            aria-valuemin="0"
            aria-valuemax="146"
          >
            <div id="progress-fill" class="progress-fill" style="width: 0%;"></div>
          </div>

          <div class="tally-meta">
            <span id="collected-text" class="mono-num">0% collected</span>
            <span>Click any cell to toggle status</span>
          </div>
        </div>
      </div>
    </section>

    <section aria-label="Checklist controls and 146-person grid" style="display: flex; flex-direction: column; gap: 1rem;">
      <div class="toolbar">
        <div class="filter-tabs" role="tablist" aria-label="Filter checklist">
          <button type="button" class="filter-tab active" data-filter="all">All (146)</button>
          <button type="button" class="filter-tab" data-filter="paid">Paid (0)</button>
          <button type="button" class="filter-tab" data-filter="unpaid">Unpaid (146)</button>
        </div>

        <div class="toolbar-right">
          <div class="legend" aria-hidden="true">
            <span class="legend-item"><span class="swatch swatch-unpaid"></span> Unpaid</span>
            <span class="legend-item">
              <span class="swatch swatch-paid">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="20 6 9 17 4 12"></polyline>
                </svg>
              </span>
              Paid
            </span>
            <span class="legend-item"><span class="swatch swatch-focus"></span> Inputted</span>
          </div>

          <div class="utility-btns">
            <button type="button" id="undo-btn" class="btn" disabled>Undo</button>
            <button type="button" id="copy-unpaid-btn" class="btn">Copy Unpaid</button>
          </div>
        </div>
      </div>

      <div id="checklist-grid" class="checklist-grid" role="grid" aria-label="Bill payment checklist for 146 people"></div>

      <div id="empty-state" class="empty-state hidden">
        <p id="empty-title" class="empty-title"></p>
        <p id="empty-desc" class="empty-desc"></p>
        <button type="button" id="empty-reset-btn" class="btn" style="margin-top: 1rem;">Show All 146</button>
      </div>
    </section>
  </main>

  <script>
    window.__INITIAL_PAID__ = <?php echo json_encode($initialPaid, JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT); ?>;
  </script>
  <script src="app.js"></script>
</body>
</html>
