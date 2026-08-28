alter table reservations
    add column status_before_displacement varchar(20);

alter table reservations
    add constraint chk_status_before_displacement
        check (
            status_before_displacement is null
                or status_before_displacement in (
                    'PENDING',
                    'APPROVED'
                )
            );
