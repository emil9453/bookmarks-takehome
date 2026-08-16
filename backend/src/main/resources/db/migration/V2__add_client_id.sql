-- Separates one install's bookmarks from another's. Not auth: the header is self-asserted, so
-- this keeps collections apart, it does not protect them. Accounts are the upgrade when the
-- data is worth protecting rather than merely separating.

-- DEFAULT before NOT NULL: H2 wants that order, Postgres accepts either. The default also
-- backfills the rows that existed before this migration into the shared collection.
alter table bookmark add column client_id varchar(64) default 'shared' not null;

-- Replaces idx_bookmark_created_at rather than joining it. Every query now filters on
-- client_id first, so an index that starts at created_at can no longer serve the list order.
create index idx_bookmark_client_created_at on bookmark (client_id, created_at desc, id desc);
drop index idx_bookmark_created_at;
