-- 로컬 일반 PostgreSQL에 Query 전용 DB/계정/스키마만 생성합니다. 기존 Account/Command 데이터는 변경하지 않습니다.
BEGIN;
DO $$
BEGIN
    IF current_database() <> 'stockdrop' OR NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'account') THEN
        RAISE EXCEPTION '로컬 일반 DB가 아닙니다. 대상 컨테이너를 확인하세요.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'product_query'
        AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR NOT rolcanlogin)) THEN
        RAISE EXCEPTION '기존 Query 역할의 권한이 예상과 다릅니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_database WHERE datname = 'product_query' AND pg_get_userbyid(datdba) <> current_user) THEN
        RAISE EXCEPTION '기존 Query DB 소유권이 예상과 다릅니다.';
    END IF;
END $$;
-- 초기화 스크립트는 이 SQL 실행 후 설정 비밀번호로 TCP 로그인을 확인해야 성공을 출력합니다.
-- 기존 역할의 비밀번호는 변경하지 않습니다.
SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'product_query') AS create_role \gset
\if :create_role
CREATE ROLE product_query LOGIN PASSWORD :'query_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
\endif
COMMIT;
SELECT NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'product_query') AS create_database \gset
\if :create_database
CREATE DATABASE product_query TEMPLATE template0 ENCODING 'UTF8';
\endif
REVOKE ALL ON DATABASE product_query FROM PUBLIC;
GRANT CONNECT ON DATABASE product_query TO product_query;
ALTER ROLE product_query IN DATABASE product_query SET search_path TO product_query;
\connect product_query
BEGIN;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA IF NOT EXISTS product_query AUTHORIZATION product_query;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='product_query' AND pg_get_userbyid(nspowner)='product_query') THEN
        RAISE EXCEPTION 'Query 스키마 소유권이 예상과 다릅니다.';
    END IF;
END $$;
REVOKE ALL ON SCHEMA product_query FROM PUBLIC;
COMMIT;
