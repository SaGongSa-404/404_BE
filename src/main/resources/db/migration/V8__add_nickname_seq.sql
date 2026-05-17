CREATE SEQUENCE user_nickname_seq;

UPDATE user_profiles
SET nickname = '너굴' || nextval('user_nickname_seq')::text;
