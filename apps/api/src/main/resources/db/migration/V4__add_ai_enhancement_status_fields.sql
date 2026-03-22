alter table practice_cards
    add column if not exists enhancement_status varchar(20) not null default 'PENDING';

alter table practice_cards
    add column if not exists enhancement_note varchar(255);

alter table practice_cards
    add column if not exists enhancement_updated_at timestamp with time zone;

create index if not exists idx_practice_cards_user_enhancement_status
    on practice_cards (user_id, enhancement_status, updated_at desc);
