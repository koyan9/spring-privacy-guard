-- Generic SQL schema for webhook replay store persistence
create table if not exists ${tableName} (
    nonce varchar(255) primary key,
    expires_at timestamp not null
);
