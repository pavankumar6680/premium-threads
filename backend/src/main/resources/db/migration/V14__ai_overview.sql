-- Phase 6: cache AI-written overviews of a recommendation run alongside its other messages.

ALTER TABLE recommendation_run_messages DROP CONSTRAINT recommendation_run_messages_kind_check;
ALTER TABLE recommendation_run_messages
    ADD CONSTRAINT ck_recommendation_run_messages_kind CHECK (kind IN ('NOTE', 'REASON', 'SUGGESTION', 'AI_OVERVIEW'));
