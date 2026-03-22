alter table practice_cards
    add column if not exists detail_title varchar(140);

alter table practice_cards
    add column if not exists detail_body text;

alter table practice_cards
    add column if not exists idea_options_json text;
