create table opportunity_cost_items (
    item_id varchar(20) primary key,
    target_category varchar(20) not null check (target_category in ('FASHION', 'BEAUTY', 'LIFE', 'DIGITAL', 'OTHER')),
    target_label varchar(40) not null,
    unit_price integer not null check (unit_price > 0),
    display_title varchar(120) not null,
    message_template text not null,
    enabled boolean not null default true,
    sort_order integer not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint chk_opportunity_cost_message_template check (position('{N}' in message_template) > 0)
);

create index idx_opportunity_cost_items_category_enabled
    on opportunity_cost_items(target_category, enabled, sort_order);

insert into opportunity_cost_items (
    item_id, target_category, target_label, unit_price, display_title, message_template, sort_order
) values
    ('ITEM_01', 'LIFE', '라이프(식음)', 5000, '시원한 아아 ☕', '이 상품 {N}개를 아끼면, 시원한 아아를 마실 수 있어요! ☕', 1),
    ('ITEM_02', 'LIFE', '라이프(문화)', 15000, '유튜브 프리미엄 1달 📺', '이 상품 {N}개를 아끼면, 광고 없이 유튜브 프리미엄을 1달 볼 수 있어요! 📺', 2),
    ('ITEM_03', 'LIFE', '라이프(식음)', 25000, '황금올리브 치킨 🍗', '이 상품 {N}개를 아끼면, 바삭한 황금올리브 치킨을 먹을 수 있어요! 🍗', 3),
    ('ITEM_04', 'LIFE', '라이프(생활)', 80000, '한 달 대중교통비 🚌', '이 상품 {N}개를 아끼면, 한 달 대중교통비(교통카드)가 해결돼요! 🚌', 4),
    ('ITEM_05', 'FASHION', '패션', 100000, '힙한 데일리 운동화(나이키/뉴발) 👟', '이 상품 {N}개를 아끼면, 힙한 데일리 운동화(나이키/뉴발)를 살 수 있어요! 👟', 5),
    ('ITEM_06', 'BEAUTY', '뷰티', 120000, '미용실 헤어 시술 💇', '이 상품 {N}개를 아끼면, 미용실 가서 예쁘게 머리를 새로 할 수 있어요! 💇', 6),
    ('ITEM_07', 'LIFE', '라이프(문화)', 150000, '콘서트 VIP석 🎤', '이 상품 {N}개를 아끼면, 좋아하는 가수의 콘서트 VIP석에 갈 수 있어요! 🎤', 7),
    ('ITEM_08', 'DIGITAL', '디지털', 300000, '최신형 무선 이어폰(에어팟/버즈) 🎧', '이 상품 {N}개를 아끼면, 최신형 무선 이어폰(에어팟/버즈)을 살 수 있어요! 🎧', 8),
    ('ITEM_09', 'LIFE', '라이프(생활)', 500000, '한 달 생활비 💸', '이 상품 {N}개를 아끼면, 한 달 동안 생활비 걱정 없이 살 수 있어요! 💸', 9),
    ('ITEM_10', 'LIFE', '라이프(여행)', 500000, '제주도 3박 4일 여행 ✈️', '이 상품 {N}개를 아끼면, 제주도 3박 4일 여행을 떠날 수 있어요! ✈️', 10),
    ('ITEM_11', 'LIFE', '라이프(생활)', 600000, '자취방 월세와 관리비 🏠', '이 상품 {N}개를 아끼면, 이번 달 자취방 월세와 관리비가 완전히 해결돼요! 🏠', 11),
    ('ITEM_12', 'DIGITAL', '디지털', 800000, '새 아이패드(또는 갤럭시탭) 📱', '이 상품 {N}개를 아끼면, 새 아이패드(또는 갤럭시탭)가 내 손에 들어와요! 📱', 12),
    ('ITEM_13', 'LIFE', '라이프(여행)', 1000000, '도쿄/방콕 해외여행 🌴', '이 상품 {N}개를 아끼면, 도쿄나 방콕으로 해외여행을 갈 수 있어요! 🌴', 13),
    ('ITEM_14', 'DIGITAL', '디지털', 2000000, '200만 원 상당 최신형 노트북 💻', '이 상품 {N}개를 아끼면, 200만 원 상당의 최신형 노트북을 살 수 있어요! 💻', 14),
    ('ITEM_15', 'OTHER', '기타(교육)', 4000000, '한 학기 대학 등록금 🎓', '이 상품 {N}개를 아끼면, 한 학기 대학 등록금을 내고도 남아요! 🎓', 15);
