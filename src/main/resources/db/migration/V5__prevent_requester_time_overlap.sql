alter table reservations
    add constraint ex_reservations_active_requester_time_overlap
    exclude using gist
    (
        requester_id with =,
        tstzrange(start_at, end_at, '[)') with &&
    )
    where (status in ('PENDING', 'APPROVED'));