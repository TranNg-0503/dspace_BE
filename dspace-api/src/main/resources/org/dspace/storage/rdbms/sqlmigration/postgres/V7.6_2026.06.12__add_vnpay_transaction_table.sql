--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-----------------------------------------------------------------------------------
-- Create table for VNPay transactions
-----------------------------------------------------------------------------------

CREATE SEQUENCE vnpay_transaction_id_seq;

CREATE TABLE vnpay_transaction
(
    id INTEGER NOT NULL,
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    bitstream_id uuid,
    amount NUMERIC(20,2),
    order_info VARCHAR(255),
    email VARCHAR(255),
    name VARCHAR(255),
    client_return_url VARCHAR(1024),
    callback_url VARCHAR(1024),
    status VARCHAR(50) NOT NULL,
    response_code VARCHAR(32),
    bank_tran_no VARCHAR(255),
    card_type VARCHAR(255),
    pay_date VARCHAR(32),
    created_at TIMESTAMP,
    CONSTRAINT vnpay_transaction_pkey PRIMARY KEY (id)
);
