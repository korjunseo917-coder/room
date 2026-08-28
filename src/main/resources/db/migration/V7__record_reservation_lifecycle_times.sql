alter table reservations
    add column expired_at timestamp with time zone;

alter table reservations
    add column completed_at timestamp with time zone;
