-- MySQL schema for privacy audit dead letter persistence
create table if not exists ${tableName} (
    id bigint primary key auto_increment,
    failed_at datetime(6) not null,
    attempts int not null,
    error_type varchar(255) not null,
    error_message text,
    occurred_at datetime(6) not null,
    action varchar(100) not null,
    resource_type varchar(100) not null,
    resource_id varchar(255),
    actor varchar(255),
    outcome varchar(50) not null,
    details_json text,
    ${tenantColumnName} varchar(255)
);
