-- Baseline schema (matches the JPA entities). Generated from the Hibernate schema of release 1.0.
-- Existing databases created by ddl-auto are baselined at this version and skip it.

CREATE TABLE customer_session (
    started_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT customer_session_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ENDED'::character varying])::text[])))
);

CREATE TABLE device_subscription (
    id uuid NOT NULL,
    session_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    auth_key character varying(255) NOT NULL,
    endpoint_url character varying(2048) NOT NULL,
    p256dh_key character varying(255) NOT NULL
);

CREATE TABLE notification (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    subscription_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    payload text NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT notification_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE "order" (
    created_at timestamp(6) with time zone NOT NULL,
    estimated_ready_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    id uuid NOT NULL,
    session_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    customer_name character varying(100),
    status character varying(255) NOT NULL,
    CONSTRAINT order_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'IN_PROGRESS'::character varying, 'READY'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE order_item (
    "position" integer NOT NULL,
    quantity integer NOT NULL,
    order_id uuid NOT NULL,
    name character varying(100) NOT NULL
);

CREATE TABLE restaurant (
    created_at timestamp(6) with time zone NOT NULL,
    tenant_id uuid NOT NULL,
    identifier character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    config_json jsonb,
    CONSTRAINT restaurant_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DEACTIVATED'::character varying])::text[])))
);

CREATE TABLE staff_account (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    password_hash character varying(100),
    role character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    CONSTRAINT staff_account_role_check CHECK (((role)::text = ANY ((ARRAY['STAFF'::character varying, 'RESTAURANT_ADMIN'::character varying])::text[]))),
    CONSTRAINT staff_account_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DEACTIVATED'::character varying])::text[])))
);

ALTER TABLE ONLY customer_session
    ADD CONSTRAINT customer_session_pkey PRIMARY KEY (id);

ALTER TABLE ONLY device_subscription
    ADD CONSTRAINT device_subscription_pkey PRIMARY KEY (id);

ALTER TABLE ONLY notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);

ALTER TABLE ONLY order_item
    ADD CONSTRAINT order_item_pkey PRIMARY KEY ("position", order_id);

ALTER TABLE ONLY "order"
    ADD CONSTRAINT order_pkey PRIMARY KEY (id);

ALTER TABLE ONLY restaurant
    ADD CONSTRAINT restaurant_identifier_key UNIQUE (identifier);

ALTER TABLE ONLY restaurant
    ADD CONSTRAINT restaurant_pkey PRIMARY KEY (tenant_id);

ALTER TABLE ONLY staff_account
    ADD CONSTRAINT staff_account_pkey PRIMARY KEY (id);

ALTER TABLE ONLY device_subscription
    ADD CONSTRAINT uq_device_subscription_tenant_session_endpoint UNIQUE (tenant_id, session_id, endpoint_url);

ALTER TABLE ONLY staff_account
    ADD CONSTRAINT uq_staff_account_username UNIQUE (username);

CREATE INDEX idx_customer_session_tenant_id ON customer_session USING btree (tenant_id);

CREATE INDEX idx_device_subscription_tenant_id ON device_subscription USING btree (tenant_id);

CREATE INDEX idx_device_subscription_tenant_session ON device_subscription USING btree (tenant_id, session_id);

CREATE INDEX idx_notification_tenant_id ON notification USING btree (tenant_id);

CREATE INDEX idx_order_tenant_id ON "order" USING btree (tenant_id);

CREATE INDEX idx_order_tenant_session ON "order" USING btree (tenant_id, session_id);

CREATE INDEX idx_restaurant_identifier ON restaurant USING btree (identifier);

CREATE INDEX idx_staff_account_tenant_id ON staff_account USING btree (tenant_id);

ALTER TABLE ONLY order_item
    ADD CONSTRAINT fks234mi6jususbx4b37k44cipy FOREIGN KEY (order_id) REFERENCES "order"(id);
