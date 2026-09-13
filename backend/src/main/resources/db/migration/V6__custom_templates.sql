-- M-Templates: custom templates owned by a User, private-by-default (only
-- visible to their creator — see TemplateService). NULL means one of the
-- three seeded built-in templates (sales_call/mock_trial/ld_debate), which
-- stay public and read-only, unaffected by this column's addition.
ALTER TABLE template ADD COLUMN created_by_user_id UUID REFERENCES app_user(id);
CREATE INDEX idx_template_created_by_user_id ON template(created_by_user_id);
