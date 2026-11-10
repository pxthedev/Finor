CREATE TABLE income_schedule (
 user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 monthly_amount NUMERIC(19,2) NOT NULL CHECK(monthly_amount>0),
 next_income DATE NOT NULL,
 remaining_essentials NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(remaining_essentials>=0),
 safety_margin NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(safety_margin>=0)
);
