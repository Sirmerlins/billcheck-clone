<?php
declare(strict_types=1);

const TOTAL_PEOPLE = 146;
const DATA_DIR = __DIR__ . '/data';
const DATA_FILE = DATA_DIR . '/paid.json';

function ensure_storage(): void {
    if (!is_dir(DATA_DIR)) {
        @mkdir(DATA_DIR, 0755, true);
    }
    if (!file_exists(DATA_FILE)) {
        @file_put_contents(
            DATA_FILE,
            json_encode(['paid' => [], 'updatedAt' => null], JSON_PRETTY_PRINT),
            LOCK_EX
        );
    }
}

function sanitize_numbers($items): array {
    if (!is_array($items)) {
        return [];
    }
    $valid = [];
    foreach ($items as $item) {
        if (is_numeric($item)) {
            $num = (int)$item;
            if ($num >= 1 && $num <= TOTAL_PEOPLE) {
                $valid[$num] = $num;
            }
        }
    }
    ksort($valid, SORT_NUMERIC);
    return array_values($valid);
}

function read_state(): array {
    ensure_storage();
    if (!is_readable(DATA_FILE)) {
        return ['paid' => [], 'updatedAt' => null];
    }
    $raw = @file_get_contents(DATA_FILE);
    if ($raw === false || trim($raw) === '') {
        return ['paid' => [], 'updatedAt' => null];
    }
    $decoded = json_decode($raw, true);
    if (!is_array($decoded)) {
        return ['paid' => [], 'updatedAt' => null];
    }
    return [
        'paid' => sanitize_numbers($decoded['paid'] ?? []),
        'updatedAt' => $decoded['updatedAt'] ?? null,
    ];
}

function write_state(array $paid): array {
    ensure_storage();
    $clean = sanitize_numbers($paid);
    $payload = [
        'paid' => $clean,
        'updatedAt' => gmdate('c'),
    ];
    $json = json_encode($payload, JSON_PRETTY_PRINT);

    $fp = @fopen(DATA_FILE, 'c+');
    if ($fp === false) {
        http_response_code(500);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode([
            'error' => 'Cannot write to data/paid.json. Please check folder permissions (chmod 755 or 775 on data/).'
        ]);
        exit;
    }

    if (flock($fp, LOCK_EX)) {
        ftruncate($fp, 0);
        rewind($fp);
        fwrite($fp, $json);
        fflush($fp);
        flock($fp, LOCK_UN);
    }
    fclose($fp);

    return $payload;
}

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$action = $_GET['action'] ?? '';

if ($method === 'GET' && $action === 'csv') {
    $state = read_state();
    $paidMap = array_flip($state['paid']);

    header('Content-Type: text/csv; charset=utf-8');
    header('Content-Disposition: attachment; filename="bill-checklist-146.csv"');

    $out = fopen('php://output', 'w');
    fputcsv($out, ['Number', 'Status']);
    for ($n = 1; $n <= TOTAL_PEOPLE; $n++) {
        fputcsv($out, [$n, isset($paidMap[$n]) ? 'Paid' : 'Unpaid']);
    }
    fclose($out);
    exit;
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate');

if ($method === 'GET') {
    echo json_encode(read_state());
    exit;
}

if ($method === 'POST') {
    $rawBody = file_get_contents('php://input');
    $body = json_decode($rawBody ?: '{}', true);
    if (!is_array($body)) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid JSON payload']);
        exit;
    }

    $postAction = $body['action'] ?? '';
    $state = read_state();
    $currentMap = array_flip($state['paid']);

    if ($postAction === 'mark') {
        $nums = sanitize_numbers($body['numbers'] ?? []);
        foreach ($nums as $n) {
            $currentMap[$n] = true;
        }
        echo json_encode(write_state(array_keys($currentMap)));
        exit;
    }

    if ($postAction === 'unmark') {
        $nums = sanitize_numbers($body['numbers'] ?? []);
        foreach ($nums as $n) {
            unset($currentMap[$n]);
        }
        echo json_encode(write_state(array_keys($currentMap)));
        exit;
    }

    if ($postAction === 'toggle') {
        $num = isset($body['number']) ? (int)$body['number'] : 0;
        if ($num < 1 || $num > TOTAL_PEOPLE) {
            http_response_code(400);
            echo json_encode(['error' => 'Number must be between 1 and 146']);
            exit;
        }
        if (isset($currentMap[$num])) {
            unset($currentMap[$num]);
        } else {
            $currentMap[$num] = true;
        }
        echo json_encode(write_state(array_keys($currentMap)));
        exit;
    }

    if ($postAction === 'set') {
        $nums = sanitize_numbers($body['numbers'] ?? []);
        echo json_encode(write_state($nums));
        exit;
    }

    if ($postAction === 'reset') {
        echo json_encode(write_state([]));
        exit;
    }

    http_response_code(400);
    echo json_encode(['error' => 'Unknown action']);
    exit;
}

http_response_code(405);
echo json_encode(['error' => 'Method not allowed']);
