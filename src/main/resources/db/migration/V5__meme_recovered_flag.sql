-- Признак записи, восстановленной сверкой библиотеки с бакетом:
-- файл в S3 нашёлся, а метаданных о нём не было.
ALTER TABLE meme_library
    ADD COLUMN recovered_from_s3 BOOLEAN NOT NULL DEFAULT FALSE;
