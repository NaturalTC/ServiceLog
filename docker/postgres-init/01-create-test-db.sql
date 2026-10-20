-- Separate database for automated tests, so they never touch development data.
-- Runs only when the database volume is first created.
CREATE DATABASE servicelog_test OWNER servicelog;
