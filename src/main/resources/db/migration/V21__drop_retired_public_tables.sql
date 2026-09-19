-- Снос осиротевших таблиц схемы public.
--
-- Отдельным файлом, потому что удаление необратимо и должно читаться одним
-- списком, а не выуживаться из правок пяти прежних миграций. Ни одна строка
-- V1-V20 здесь не переписана: история схемы остаётся тем, чем была.
--
-- Условие для каждой таблицы одно и то же и проверено грепом по всему
-- ru/hothat: у неё не осталось ни сущности JPA, ни запроса, ни адреса. Всё,
-- что здесь перечислено, имеет живого двойника в схеме v2, и двойник уже
-- работает — это он отвечает браузеру сегодня.
--
-- Данные НЕ переносятся, и это то же решение, что в V20 (§11 плана): v2 —
-- отдельный деплой со своей базой, учётки заводятся заново. Переносить в
-- новую базу заявки, переписки и составы команд, заведённые чужими учётками
-- в старой, значило бы показать игроку чужое.


-- ─────────────── ушли вместе со своими адресами ───────────────
--
-- Эти четырнадцать освободились ровно сейчас: вместе с ними снесены
-- контроллеры, сервисы, репозитории и сущности, которые их вели.
--
--   analytics_event   → v2.analytics_event   (POST /api/v2/app/analytics-events)
--   feature_flag      → v2.feature_flag      (GET  /api/v2/app/features/{name})
--   meme_library      → v2.meme              (область /api/v2/media)
--   direct_chat       → v2.direct_chat       (область /api/v2/chat)
--   direct_chat_message → v2.direct_chat_message
--   friend_request    → v2.friend_request    (область /api/v2/friends)
--   ranked_team       → v2.ranked_team       (область /api/v2/team)
--   ranked_team_name  → v2.ranked_team       (уникальный ключ вместо таблицы-индекса)
--   team_invite       → v2.team_invite
--   ranked_team_preflight → v2.team_preflight_session + team_preflight_participant
--   season_ranking, season_ranking_team, season_ranking_player
--                     → v2.ranking_board, season_team_standing, season_player_standing
--   ranked_result_event → v2.match_result_event
--
-- Имя схемы написано явно у каждой строки. В v2 лежат таблицы тех же имён —
-- direct_chat, friend_request, ranked_team, refresh_token и ещё пять, — и
-- цена ошибки в разрешении имени здесь не «упало на миграции», а «снесли
-- живую таблицу нового кластера».
--
-- Порядок важен один раз: direct_chat_message ссылается на direct_chat
-- внешним ключом direct_chat_message_pair_fkey, поэтому дочерняя уходит
-- первой. Остальные тринадцать ни с чем в public не связаны.
drop table if exists public.direct_chat_message;
drop table if exists public.direct_chat;

drop table if exists public.analytics_event;
drop table if exists public.feature_flag;
drop table if exists public.meme_library;
drop table if exists public.friend_request;
drop table if exists public.ranked_team_preflight;
drop table if exists public.ranked_result_event;
drop table if exists public.season_ranking_player;
drop table if exists public.season_ranking_team;
drop table if exists public.season_ranking;
drop table if exists public.team_invite;
drop table if exists public.ranked_team_name;
drop table if exists public.ranked_team;


-- ─────────────── были мертвы и до этого шага ───────────────
--
-- Эти семь не читал и не писал никто уже к началу сноса: их работу забрали
-- кластеры v2 в миграциях V13-V16, а старые таблицы остались стоять пустыми.
--
--   nickname_index        → v2.player_profile (имя и его уникальность в одной строке)
--   support_request       → v2.nickname_change_request
--   sabotage_game_use     → v2.sabotage_game_grant
--   user_ban              → v2.user_ban
--   legal_consent         → v2.legal_consent
--   refresh_token         → v2.refresh_token
--   password_reset_token  → v2.password_reset_token
--
-- refresh_token и password_reset_token ссылаются на app_user, но она
-- остаётся: с неё живёт прежний документный шлюз комнаты. Уходят дети, не
-- родитель, поэтому снимать ключи отдельно не нужно.
drop table if exists public.nickname_index;
drop table if exists public.support_request;
drop table if exists public.sabotage_game_use;
drop table if exists public.user_ban;
drop table if exists public.legal_consent;
drop table if exists public.refresh_token;
drop table if exists public.password_reset_token;
