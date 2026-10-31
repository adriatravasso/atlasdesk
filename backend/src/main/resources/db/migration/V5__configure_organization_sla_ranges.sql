ALTER TABLE organizations
    ALTER COLUMN sla_high_hours SET DEFAULT 8,
    DROP CONSTRAINT ck_organizations_sla_low_positive,
    DROP CONSTRAINT ck_organizations_sla_medium_positive,
    DROP CONSTRAINT ck_organizations_sla_high_positive,
    ADD CONSTRAINT ck_organizations_sla_low_range CHECK (sla_low_hours BETWEEN 1 AND 720),
    ADD CONSTRAINT ck_organizations_sla_medium_range CHECK (sla_medium_hours BETWEEN 1 AND 720),
    ADD CONSTRAINT ck_organizations_sla_high_range CHECK (sla_high_hours BETWEEN 1 AND 720);
