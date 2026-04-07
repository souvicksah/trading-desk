-- Seed traders — safe to re-run (idempotent).
MERGE INTO traders t
USING (SELECT 'T004' AS id, 'Souvick' AS name FROM dual) src
ON (t.id = src.id)
WHEN NOT MATCHED THEN INSERT (id, name) VALUES (src.id, src.name);