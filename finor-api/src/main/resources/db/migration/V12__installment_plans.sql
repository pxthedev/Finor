CREATE TABLE installment_plan (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  installment_amount NUMERIC(19,2) NOT NULL CHECK (installment_amount > 0),
  total_installments INT NOT NULL CHECK (total_installments BETWEEN 1 AND 360),
  paid_installments INT NOT NULL DEFAULT 0 CHECK (paid_installments >= 0 AND paid_installments <= total_installments),
  first_due_date DATE NOT NULL
);
CREATE INDEX installment_plan_user_idx ON installment_plan(user_id);
