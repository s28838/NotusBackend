ALTER TABLE group_invitations
    DROP CONSTRAINT IF EXISTS group_invitations_status_check;

ALTER TABLE group_invitations
    ADD CONSTRAINT group_invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'CANCELLED', 'FAILED'));
