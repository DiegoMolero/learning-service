-- Initialization script for PostgreSQL
-- This script runs when the container starts for the first time

-- Create the database if it doesn't exist (though POSTGRES_DB should handle this)
-- CREATE DATABASE IF NOT EXISTS authdb;

-- Set up any initial database configuration
-- For example, create extensions if needed
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- The Exposed ORM will handle table creation automatically
-- So we don't need to create tables here
