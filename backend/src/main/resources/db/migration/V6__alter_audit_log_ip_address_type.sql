-- Change audit_log.ip_address from INET to VARCHAR(45)
-- This resolves Hibernate Reactive mapping issues while maintaining IPv4/IPv6 support

ALTER TABLE audit_log
    ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::VARCHAR;

COMMENT ON COLUMN audit_log.ip_address IS 'IP address as VARCHAR(45) - supports IPv4 and IPv6 addresses';
