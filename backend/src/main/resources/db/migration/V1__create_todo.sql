create table todo (
    id         uuid         primary key,
    title      varchar(200) not null,
    completed  boolean      not null default false,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    version    bigint       not null
);

create index todo_created_at_idx on todo (created_at);
