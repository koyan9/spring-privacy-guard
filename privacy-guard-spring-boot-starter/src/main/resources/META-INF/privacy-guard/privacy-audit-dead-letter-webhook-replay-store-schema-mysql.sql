-- MySQL schema for webhook replay store persistence
create table if not exists ${tableName} (
    nonce varchar(255) primary key,
    expires_at datetime(6) not null
);
create index idx_privacy_audit_replay_expires_at on ${tableName} (expires_at);
