-- 상품 조회 서비스의 최초 테이블입니다. 엔티티 enum 값은 애플리케이션이 검증하므로 DB CHECK로 고정하지 않습니다.

create table product_query.inquiries (
    answered_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone,
    projected_at timestamp(6) with time zone,
    source_version bigint not null,
    buyer_id uuid,
    id uuid not null,
    product_id uuid,
    payload_hash varchar(64),
    question varchar(2000),
    answer varchar(8000),
    primary key (id)
);

create table product_query.processed_events (
    aggregate_version bigint not null,
    lock_version bigint not null,
    processed_at timestamp(6) with time zone,
    aggregate_id uuid,
    event_id uuid not null,
    event_hash varchar(64) not null,
    primary key (event_id)
);

create table product_query.products (
    available_quantity integer not null,
    confirmed_quantity integer not null,
    initial_quantity integer not null,
    published boolean not null,
    reserved_quantity integer not null,
    ends_at timestamp(6) with time zone,
    price bigint not null,
    projected_at timestamp(6) with time zone,
    published_at timestamp(6) with time zone,
    source_occurred_at timestamp(6) with time zone,
    source_version bigint not null,
    starts_at timestamp(6) with time zone,
    id uuid not null,
    payload_hash varchar(64),
    brand_or_store varchar(200),
    category varchar(200),
    name varchar(200),
    snapshot text,
    primary key (id)
);

create index idx_query_inquiry_owner
   on product_query.inquiries (product_id, buyer_id);

create index idx_query_sale
   on product_query.products (published, starts_at, ends_at);

create index idx_query_category
   on product_query.products (category);
