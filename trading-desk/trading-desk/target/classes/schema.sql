-- ============================================================
-- Trading Desk Schema for Oracle
-- ============================================================

-- 1️⃣ Traders: owning entity for orders and portfolio positions
CREATE TABLE TRADERS (
                         ID   VARCHAR2(50) PRIMARY KEY,
                         NAME VARCHAR2(255) NOT NULL
);

-- 2️⃣ Orders: immutable audit trail; status transitions PENDING→FILLED / PENDING→CANCELLED
-- version column supports optimistic-lock detection (@Version in Hibernate)
CREATE TABLE ORDERS (
                        ID         VARCHAR2(36) PRIMARY KEY,        -- UUID stored as string
                        TRADER_ID  VARCHAR2(50) NOT NULL,
                        STOCK      VARCHAR2(20) NOT NULL,
                        SECTOR     VARCHAR2(50) NOT NULL,
                        QUANTITY   NUMBER(10) NOT NULL,
                        SIDE       VARCHAR2(10) NOT NULL,
                        STATUS     VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
                        CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        VERSION    NUMBER(19) DEFAULT 0 NOT NULL,

                        CONSTRAINT FK_ORDER_TRADER FOREIGN KEY (TRADER_ID) REFERENCES TRADERS(ID),
                        CONSTRAINT CHK_ORDER_QTY CHECK (QUANTITY > 0),
                        CONSTRAINT CHK_ORDER_SIDE CHECK (SIDE IN ('BUY','SELL')),
                        CONSTRAINT CHK_ORDER_STATUS CHECK (STATUS IN ('PENDING','FILLED','CANCELLED'))
);

-- Index for fast queries on orders
CREATE INDEX IDX_ORDERS_TRADER_STATUS ON ORDERS (TRADER_ID, STATUS);

-- 3️⃣ Portfolio: one row per (trader, stock) pair; current net holding
CREATE TABLE PORTFOLIO (
                           ID        VARCHAR2(36) PRIMARY KEY,          -- UUID stored as string
                           TRADER_ID VARCHAR2(50) NOT NULL,
                           STOCK     VARCHAR2(20) NOT NULL,
                           SECTOR    VARCHAR2(50) NOT NULL,
                           QUANTITY  NUMBER(10) DEFAULT 0 NOT NULL,

                           CONSTRAINT FK_PORTFOLIO_TRADER FOREIGN KEY (TRADER_ID) REFERENCES TRADERS(ID),
                           CONSTRAINT UQ_PORTFOLIO_TRADER_STOCK UNIQUE (TRADER_ID, STOCK),
                           CONSTRAINT CHK_PORTFOLIO_QTY CHECK (QUANTITY >= 0)
);

-- Index for fast queries on portfolio
CREATE INDEX IDX_PORTFOLIO_TRADER ON PORTFOLIO (TRADER_ID);