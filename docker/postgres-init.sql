\set ON_ERROR_STOP on

SELECT 'CREATE DATABASE wallet OWNER sportsbook'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wallet')\gexec

SELECT 'CREATE DATABASE betting OWNER sportsbook'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'betting')\gexec

SELECT 'CREATE DATABASE settlement OWNER sportsbook'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'settlement')\gexec

SELECT 'CREATE DATABASE admin OWNER sportsbook'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'admin')\gexec
