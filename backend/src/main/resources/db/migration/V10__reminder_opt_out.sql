-- When a customer unsubscribed from tune-up reminder emails (null: still subscribed)
ALTER TABLE customer ADD COLUMN reminders_opted_out_at TIMESTAMPTZ;
