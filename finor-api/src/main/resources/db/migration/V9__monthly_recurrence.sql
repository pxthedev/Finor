CREATE TABLE recurring_rule(id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,name VARCHAR(120) NOT NULL,amount NUMERIC(19,2) NOT NULL CHECK(amount>0),first_date DATE NOT NULL,active BOOLEAN NOT NULL DEFAULT true,UNIQUE(id,user_id));
ALTER TABLE recurring_entry ADD COLUMN rule_id UUID;
ALTER TABLE recurring_entry ADD FOREIGN KEY(rule_id,user_id) REFERENCES recurring_rule(id,user_id);
CREATE UNIQUE INDEX recurring_occurrence_idx ON recurring_entry(rule_id,due_date) WHERE rule_id IS NOT NULL;
