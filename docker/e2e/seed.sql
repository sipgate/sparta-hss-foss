PRAGMA foreign_keys = ON;

DROP TABLE IF EXISTS roaming_blocked_event;
DROP TABLE IF EXISTS roaming_blocked_location_lte;
DROP TABLE IF EXISTS roaming_blocked_location;
DROP TABLE IF EXISTS roaming_blocked_imsi_override_lte;
DROP TABLE IF EXISTS roaming_blocked_imsi_override;
DROP TABLE IF EXISTS tac_profile;
DROP TABLE IF EXISTS location_ip_sm_gw;
DROP TABLE IF EXISTS location_vowifi;
DROP TABLE IF EXISTS location_lte;
DROP TABLE IF EXISTS imsi_scscf;
DROP TABLE IF EXISTS imsi_profile;
DROP TABLE IF EXISTS imsi;
DROP TABLE IF EXISTS sim;
DROP TABLE IF EXISTS msisdn;

CREATE TABLE IF NOT EXISTS msisdn (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  msisdn VARCHAR(45) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS sim (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  msisdn INTEGER NOT NULL,
  secretKey BLOB NOT NULL,
  sqn BIGINT DEFAULT 0,
  op BLOB NOT NULL,
  ss_cw TEXT DEFAULT 'active',
  iccid CHAR(20) NOT NULL DEFAULT '',
  CONSTRAINT fk_sim_msisdn FOREIGN KEY (msisdn) REFERENCES msisdn (id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS imsi (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  imsi CHAR(15) NOT NULL UNIQUE,
  simId INTEGER,
  state TEXT NOT NULL DEFAULT 'inactive',
  type TEXT NOT NULL,
  assignedAt TIMESTAMP NULL DEFAULT NULL,
  CONSTRAINT fk_imsi_sim FOREIGN KEY (simId) REFERENCES sim (id) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS imsi_profile (
  imsiId INTEGER NOT NULL PRIMARY KEY,
  lastUpdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  name VARCHAR(255) NOT NULL,
  CONSTRAINT imsi_profile_imsi_id_fk FOREIGN KEY (imsiId) REFERENCES imsi (id)
);

CREATE TABLE IF NOT EXISTS imsi_scscf (
  imsiId INTEGER NOT NULL PRIMARY KEY,
  scscf VARCHAR(255) NOT NULL,
  diameter_host VARCHAR(255) NOT NULL,
  diameter_realm VARCHAR(255) NOT NULL,
  lastUpdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT imsi_scscf_imsi_id_fk FOREIGN KEY (imsiId) REFERENCES imsi (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS location_lte (
  imsiId INTEGER NOT NULL PRIMARY KEY,
  mmeHostname VARCHAR(255) NOT NULL,
  mmeRealm VARCHAR(255) NOT NULL,
  visitedPlmnId VARCHAR(255) DEFAULT NULL,
  tac VARCHAR(8) DEFAULT NULL,
  lastUpdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT location_lte_imsi_id_fk FOREIGN KEY (imsiId) REFERENCES imsi (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS location_vowifi (
  imsiId INTEGER NOT NULL PRIMARY KEY,
  aaa_server_name VARCHAR(255) NOT NULL,
  diameter_host VARCHAR(255) NOT NULL,
  diameter_realm VARCHAR(255) NOT NULL,
  lastUpdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT location_vowifi_imsi_id_fk FOREIGN KEY (imsiId) REFERENCES imsi (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS location_ip_sm_gw (
  imsiId INTEGER NOT NULL PRIMARY KEY,
  ipSmGwName VARCHAR(255) NOT NULL,
  ipSmGwRealm VARCHAR(255) NOT NULL,
  lastUpdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT location_ip_sm_gw_imsi_id_fk FOREIGN KEY (imsiId) REFERENCES imsi (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tac_profile (
  tac VARCHAR(8) NOT NULL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  lastUpdate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS roaming_blocked_imsi_override (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  imsi CHAR(20) NOT NULL,
  gt_prefix VARCHAR(20) NOT NULL,
  roaming CHAR(10) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  UNIQUE (imsi, gt_prefix)
);

CREATE TABLE IF NOT EXISTS roaming_blocked_imsi_override_lte (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  imsi CHAR(20) NOT NULL,
  mcc VARCHAR(3) NOT NULL,
  mnc VARCHAR(3) NOT NULL,
  roaming CHAR(10) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  UNIQUE (imsi, mcc, mnc)
);

CREATE TABLE IF NOT EXISTS roaming_blocked_location (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  gt_prefix VARCHAR(20) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  UNIQUE (gt_prefix)
);

CREATE TABLE IF NOT EXISTS roaming_blocked_location_lte (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  mcc VARCHAR(3) NOT NULL,
  mnc VARCHAR(3) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  UNIQUE (mcc, mnc)
);

CREATE TABLE IF NOT EXISTS roaming_blocked_event (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  imsi CHAR(20) NOT NULL,
  mcc VARCHAR(20) NOT NULL,
  component VARCHAR(20) NOT NULL DEFAULT 'hss',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed subscribers (Ki/OP/SQN placeholders; tests overwrite via SubscriberDb):
--   001010000000001: the Cx/S6a happy-path subscriber
--   001010000000002: the ISD profile-change subscriber
INSERT INTO msisdn (id, msisdn) VALUES (1, '4915790000001');
INSERT INTO msisdn (id, msisdn) VALUES (2, '4915790000002');
INSERT INTO sim (id, msisdn, secretKey, sqn, op, ss_cw, iccid)
  VALUES (1, 1, x'000102030405060708090A0B0C0D0E0F', 0, x'0F0E0D0C0B0A09080706050403020100', 'active', '');
INSERT INTO sim (id, msisdn, secretKey, sqn, op, ss_cw, iccid)
  VALUES (2, 2, x'000102030405060708090A0B0C0D0E0F', 0, x'0F0E0D0C0B0A09080706050403020100', 'active', '');
INSERT INTO imsi (id, imsi, simId, state, type) VALUES (1, '001010000000001', 1, 'active', 'main');
INSERT INTO imsi (id, imsi, simId, state, type) VALUES (2, '001010000000002', 2, 'active', 'main');
