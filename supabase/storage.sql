-- Nearness — Storage bucket + policies for whiteboard media.
-- Run after schema.sql and rls.sql.

-- Private bucket (not public). Photos and voice memos live here.
insert into storage.buckets (id, name, public)
values ('whiteboard-media', 'whiteboard-media', false)
on conflict (id) do nothing;

-- Path convention: {pair_key}/{item_id}/{file}
-- A user may read/write objects whose pair_key contains their own uid.
-- pair_key is "uidA_uidB" (sorted), so a simple membership check works.

drop policy if exists whiteboard_media_read on storage.objects;
create policy whiteboard_media_read on storage.objects
    for select using (
        bucket_id = 'whiteboard-media'
        and (storage.foldername(name))[1] like '%' || auth.uid()::text || '%'
    );

drop policy if exists whiteboard_media_write on storage.objects;
create policy whiteboard_media_write on storage.objects
    for insert with check (
        bucket_id = 'whiteboard-media'
        and (storage.foldername(name))[1] like '%' || auth.uid()::text || '%'
    );

drop policy if exists whiteboard_media_delete on storage.objects;
create policy whiteboard_media_delete on storage.objects
    for delete using (
        bucket_id = 'whiteboard-media'
        and (storage.foldername(name))[1] like '%' || auth.uid()::text || '%'
    );
