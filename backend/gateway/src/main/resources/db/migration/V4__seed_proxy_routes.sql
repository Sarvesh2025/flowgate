INSERT INTO routes (
    id, route_key, path_pattern, methods, rewrite_prefix, lb_algorithm, canary_percent, enabled
) VALUES
    ('11111111-1111-1111-1111-111111111111', 'orders', '/gw/orders/**', 'GET', '/api/orders', 'ROUND_ROBIN', 0, TRUE),
    ('22222222-2222-2222-2222-222222222222', 'payments', '/gw/payments/**', 'GET', '/api/payments', 'WEIGHTED', 20, TRUE);

INSERT INTO upstream_targets (id, route_id, url, weight, canary, healthy) VALUES
    ('a1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'http://127.0.0.1:8081', 1, FALSE, TRUE),
    ('a1111111-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111111', 'http://localhost:8081', 1, FALSE, TRUE),
    ('b2222222-2222-2222-2222-222222222221', '22222222-2222-2222-2222-222222222222', 'http://localhost:8082', 3, FALSE, TRUE),
    ('b2222222-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'http://127.0.0.1:8082', 1, TRUE, TRUE);
