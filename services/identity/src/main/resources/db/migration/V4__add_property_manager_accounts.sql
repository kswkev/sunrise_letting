-- Seeded Property Manager accounts start with a temporary password, which they must change at first sign-in.
ALTER TABLE accounts ADD COLUMN password_temporary boolean NOT NULL DEFAULT false;

-- Property Manager accounts have an email and a password only. Visitors must still have the profile fields.
ALTER TABLE accounts ALTER COLUMN title DROP NOT NULL;
ALTER TABLE accounts ALTER COLUMN first_name DROP NOT NULL;
ALTER TABLE accounts ALTER COLUMN surname DROP NOT NULL;
ALTER TABLE accounts ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE accounts ADD CONSTRAINT accounts_visitor_profile_required CHECK (
    role <> 'VISITOR'
    OR (title IS NOT NULL AND first_name IS NOT NULL AND surname IS NOT NULL AND phone IS NOT NULL)
);
