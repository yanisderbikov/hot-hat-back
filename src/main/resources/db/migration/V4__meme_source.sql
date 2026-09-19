-- Откуда взялся мем: клиент пишет эти поля при добавлении в библиотеку.
ALTER TABLE meme_library
    ADD COLUMN source_url  VARCHAR(2048),
    ADD COLUMN import_mode VARCHAR(24);
