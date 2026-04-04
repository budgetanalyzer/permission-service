ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE users ADD COLUMN deactivated_by VARCHAR(50);

COMMENT ON COLUMN users.status IS 'User access control state: ACTIVE, DEACTIVATED';
COMMENT ON COLUMN users.deactivated_at IS 'Timestamp when user was deactivated';
COMMENT ON COLUMN users.deactivated_by IS 'User ID who triggered deactivation';

CREATE INDEX idx_users_idp_sub_status ON users(idp_sub, status);
