SELECT 'CREATE ROLE admin LOGIN PASSWORD ''admin123'''
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'admin')\gexec

SELECT 'CREATE DATABASE ai_ecommerce OWNER admin'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ai_ecommerce')\gexec

SELECT 'CREATE DATABASE litellm OWNER admin'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'litellm')\gexec

\connect ai_ecommerce
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
