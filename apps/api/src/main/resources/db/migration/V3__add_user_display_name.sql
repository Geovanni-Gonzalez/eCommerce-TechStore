-- BKL-015: el contrato público de registro incluye displayName.
-- Mantener V1/V2 inalterables. Los usuarios de V2 reciben un nombre neutro;
-- las altas nuevas deben indicar un nombre explícito desde el caso de uso.
ALTER TABLE users
    ADD COLUMN display_name VARCHAR(120) NOT NULL DEFAULT 'Cliente';

ALTER TABLE users
    ADD CONSTRAINT chk_users_display_name CHECK (
        CHAR_LENGTH(display_name) BETWEEN 2 AND 120
        AND display_name = BTRIM(display_name)
        AND display_name !~ '[[:cntrl:]]'
    );
