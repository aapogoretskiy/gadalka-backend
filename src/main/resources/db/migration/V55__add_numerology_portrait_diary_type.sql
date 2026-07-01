-- Добавляем новый тип в enum diary_feature_type для хранения событий портрета личности.
-- PostgreSQL позволяет добавлять значения в enum без пересоздания типа.
ALTER TYPE diary_feature_type ADD VALUE 'NUMEROLOGY_PORTRAIT';
