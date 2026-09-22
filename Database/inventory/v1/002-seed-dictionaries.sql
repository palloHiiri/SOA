--liquibase formatted sql

--changeset fuzis:inventory-002

INSERT INTO train_set_lifecycle_statuses (code, name, description) VALUES
    ('DRAFT', 'Draft', 'Train set is being created or configured and is not available to downstream systems.'),
    ('ACTIVE', 'Active', 'Train set is complete and may be published to downstream systems.'),
    ('MAINTENANCE', 'Maintenance', 'Train set is temporarily unavailable while important corrections are being made.'),
    ('RETIRED', 'Retired', 'Train set is permanently retired and must not be used for new operations.');

INSERT INTO train_set_lifecycle_transitions (
    train_set_lifecycle_statuses_from,
    train_set_lifecycle_status_to
)
SELECT src.id, dst.id
FROM train_set_lifecycle_statuses src
JOIN train_set_lifecycle_statuses dst ON dst.code = 'ACTIVE'
WHERE src.code = 'DRAFT'
UNION ALL
SELECT src.id, dst.id
FROM train_set_lifecycle_statuses src
JOIN train_set_lifecycle_statuses dst ON dst.code = 'MAINTENANCE'
WHERE src.code = 'ACTIVE'
UNION ALL
SELECT src.id, dst.id
FROM train_set_lifecycle_statuses src
JOIN train_set_lifecycle_statuses dst ON dst.code = 'ACTIVE'
WHERE src.code = 'MAINTENANCE'
UNION ALL
SELECT src.id, dst.id
FROM train_set_lifecycle_statuses src
JOIN train_set_lifecycle_statuses dst ON dst.code = 'RETIRED'
WHERE src.code = 'ACTIVE'
UNION ALL
SELECT src.id, dst.id
FROM train_set_lifecycle_statuses src
JOIN train_set_lifecycle_statuses dst ON dst.code = 'RETIRED'
WHERE src.code = 'MAINTENANCE';
