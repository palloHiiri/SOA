--liquibase formatted sql

--changeset fuzis:clients-document-types
INSERT INTO document_types(code, name, description) VALUES
    ('PASSPORT_RU', 'Паспорт гражданина РФ', 'Основной документ гражданина Российской Федерации'),
    ('BIRTH_CERTIFICATE', 'Свидетельство о рождении', 'Свидетельство о рождении'),
    ('FOREIGN_PASSPORT', 'Заграничный паспорт', 'Паспорт для выезда за пределы Российской Федерации'),
    ('FOREIGN_ID', 'Документ иностранного гражданина', 'Документ, удостоверяющий личность иностранного гражданина')
ON CONFLICT (code) DO NOTHING;
