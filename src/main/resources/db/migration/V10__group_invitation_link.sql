ALTER TABLE group_invitations
    ADD COLUMN IF NOT EXISTS invitation_link TEXT;
