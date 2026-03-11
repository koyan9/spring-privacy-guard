-- Generic SQL schema for webhook replay store persistence
create table if not exists ${tableName} (
    nonce varchar(255) primary key,
    expires_at timestamp not null
);
create index if not exists idx_privacy_audit_replay_expires_at on ${tableName} (expires_at);
