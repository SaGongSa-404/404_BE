alter table mascot_profiles
    add column welcome_bubble_seen boolean;

update mascot_profiles
set welcome_bubble_seen = true
where welcome_bubble_seen is null;

alter table mascot_profiles
    alter column welcome_bubble_seen set default false,
    alter column welcome_bubble_seen set not null;
