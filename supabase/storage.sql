-- Nearness — Storage buckets + policies (v2)
-- Run after schema.sql and rls.sql (uses is_partnership_member / my_partner_id).

-- whiteboard-media: private. Path convention {partnership_id}/{item_id}/{file}.
insert into storage.buckets (id, name, public)
values ('whiteboard-media', 'whiteboard-media', false)
on conflict (id) do nothing;

drop policy if exists whiteboard_media_read on storage.objects;
create policy whiteboard_media_read on storage.objects for select to authenticated using (
    bucket_id = 'whiteboard-media'
    and public.is_partnership_member((storage.foldername(name))[1]::uuid)
);
drop policy if exists whiteboard_media_write on storage.objects;
create policy whiteboard_media_write on storage.objects for insert to authenticated with check (
    bucket_id = 'whiteboard-media'
    and public.is_partnership_member((storage.foldername(name))[1]::uuid)
);
drop policy if exists whiteboard_media_delete on storage.objects;
create policy whiteboard_media_delete on storage.objects for delete to authenticated using (
    bucket_id = 'whiteboard-media'
    and public.is_partnership_member((storage.foldername(name))[1]::uuid)
);

-- avatars: private. Path convention {uid}.jpg. Owner-based (self + partner read).
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', false)
on conflict (id) do nothing;

drop policy if exists avatars_read on storage.objects;
create policy avatars_read on storage.objects for select to authenticated using (
    bucket_id = 'avatars' and (owner = auth.uid() or owner = public.my_partner_id())
);
drop policy if exists avatars_write on storage.objects;
create policy avatars_write on storage.objects for insert to authenticated with check (
    bucket_id = 'avatars' and owner = auth.uid()
);
drop policy if exists avatars_update on storage.objects;
create policy avatars_update on storage.objects for update to authenticated using (
    bucket_id = 'avatars' and owner = auth.uid()
);
drop policy if exists avatars_delete on storage.objects;
create policy avatars_delete on storage.objects for delete to authenticated using (
    bucket_id = 'avatars' and owner = auth.uid()
);
