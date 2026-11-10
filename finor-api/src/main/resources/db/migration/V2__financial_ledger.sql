CREATE TABLE category (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 name VARCHAR(80) NOT NULL, UNIQUE(id,user_id), UNIQUE(user_id,name)
);
CREATE TABLE financial_transaction (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 account_id UUID NOT NULL, destination_account_id UUID, category_id UUID,
 type VARCHAR(10) NOT NULL CHECK(type IN ('INCOME','EXPENSE','TRANSFER')),
 amount NUMERIC(19,2) NOT NULL CHECK(amount>0), date DATE NOT NULL,
 description VARCHAR(300), idempotency_key UUID NOT NULL,
 UNIQUE(user_id,idempotency_key),
 FOREIGN KEY(account_id,user_id) REFERENCES account(id,user_id),
 FOREIGN KEY(destination_account_id,user_id) REFERENCES account(id,user_id),
 FOREIGN KEY(category_id,user_id) REFERENCES category(id,user_id),
 CHECK((type='TRANSFER' AND destination_account_id IS NOT NULL AND destination_account_id<>account_id AND category_id IS NULL) OR (type<>'TRANSFER' AND destination_account_id IS NULL))
);
CREATE INDEX transaction_user_date_idx ON financial_transaction(user_id,date);
CREATE INDEX transaction_category_idx ON financial_transaction(user_id,category_id,date);
CREATE TABLE financial_goal (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 name VARCHAR(120) NOT NULL, target NUMERIC(19,2) NOT NULL CHECK(target>0),
 reserved NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(reserved>=0), deadline DATE
);
CREATE TABLE recurring_entry (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 name VARCHAR(120) NOT NULL, amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
 due_date DATE NOT NULL, paid BOOLEAN NOT NULL DEFAULT FALSE
);
