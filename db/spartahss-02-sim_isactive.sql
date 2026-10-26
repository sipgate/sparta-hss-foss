-- this field was never used in production
ALTER TABLE imsi DROP COLUMN state;

-- this field will indicate if a SIM is active or not, including all its IMSIs
ALTER TABLE sim ADD COLUMN isActive bit(1) NOT NULL DEFAULT b'0';
