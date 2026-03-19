-- MySQL schema for privacy audit persistence
create table if not exists ${tableName} (
    id bigint not null auto_increment primary key,
    occurred_at timestamp not null,
    action varchar(100) not null,
    resource_type varchar(100) not null,
    resource_id varchar(255),
    actor varchar(255),
    outcome varchar(50) not null,
    details_json longtext,
    ${tenantColumnName} varchar(255)
);
