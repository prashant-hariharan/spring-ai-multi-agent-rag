-- Ensure required PostgreSQL extensions are available in ai_ecommerce database.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Spring AI PgVector default table structure with JSONB metadata.
CREATE TABLE IF NOT EXISTS public.vector_store (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content text,
    metadata jsonb,
    embedding vector(1536)
);

-- HNSW index for cosine similarity search.
-- Matches spring.ai.vectorstore.pgvector.index-type=HNSW
-- and spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw_cosine_idx
    ON public.vector_store
    USING hnsw (embedding vector_cosine_ops);

-- Generic JSONB index for flexible metadata filtering (contains/path queries).
CREATE INDEX IF NOT EXISTS vector_store_metadata_gin_idx
    ON public.vector_store
    USING gin (metadata jsonb_path_ops);


CREATE INDEX IF NOT EXISTS vector_store_metadata_file_name_idx
    ON public.vector_store ((metadata ->> 'fileName'));

CREATE TABLE IF NOT EXISTS public.rag_document_catalog (
    document_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name varchar(255) NOT NULL,
    document_type varchar(50) NOT NULL,
    source_system varchar(100) NOT NULL,
    indexed_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rag_doc_catalog_file_type UNIQUE (file_name, document_type)
);

CREATE INDEX IF NOT EXISTS rag_document_catalog_document_type_idx
    ON public.rag_document_catalog (document_type);

-- Order management table for JPA-backed OrderService
CREATE TABLE IF NOT EXISTS public.orders (
    order_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number varchar(20) NOT NULL UNIQUE,
    customer_name varchar(150) NOT NULL,
    product_name varchar(150) NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status varchar(30) NOT NULL,
    delivery_info text
);

CREATE INDEX IF NOT EXISTS orders_order_number_idx
    ON public.orders (order_number);

CREATE INDEX IF NOT EXISTS orders_status_idx
    ON public.orders (status);
