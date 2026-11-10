CREATE TABLE user_photo (
  user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  data_url TEXT NOT NULL CHECK (length(data_url) <= 350000)
);
