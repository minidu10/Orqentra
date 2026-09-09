CREATE TABLE restaurants (
    id          BIGSERIAL     PRIMARY KEY,
    reference   VARCHAR(36)   NOT NULL UNIQUE,
    name        VARCHAR(255)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id             BIGSERIAL     PRIMARY KEY,
    email          VARCHAR(255)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255)  NOT NULL,
    restaurant_id  BIGINT        NOT NULL REFERENCES restaurants(id),
    role           VARCHAR(32)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

INSERT INTO restaurants (reference, name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Alice Trattoria'),
    ('22222222-2222-2222-2222-222222222222', 'Bob Bistro');

-- Pre-computed BCrypt hashes. Dev passwords are alice-pass, bob-pass and admin-pass;
-- no plaintext is ever stored.
INSERT INTO users (email, password_hash, restaurant_id, role) VALUES
    ('alice@orqentra.test',
     '$2a$10$Zvi1qNVYseB.DHdcqdurWOHOsLBrE56bwhOdNChMyh473m7VSREgq',
     (SELECT id FROM restaurants WHERE reference = '11111111-1111-1111-1111-111111111111'),
     'RESTAURANT'),
    ('bob@orqentra.test',
     '$2a$10$nP51MTKzrlKVQN.dJxgHG.5Owxdf/RxYZbT9zE/nfb2.uGMJtc.6u',
     (SELECT id FROM restaurants WHERE reference = '22222222-2222-2222-2222-222222222222'),
     'RESTAURANT'),
    ('admin@orqentra.test',
     '$2a$10$m4Ow.dWDv7surMrYnN6/N.zi4x6cNta9p87v.hYEgrQbGQCsQMzLO',
     (SELECT id FROM restaurants WHERE reference = '11111111-1111-1111-1111-111111111111'),
     'ADMIN');
