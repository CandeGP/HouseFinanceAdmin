-- Bucket público para distribuir el APK de la app (sin pasar por Play Store ni por GitHub).
-- Es público de lectura por URL directa; subir archivos solo se puede con la llave de servicio
-- (CLI de Supabase), así que ninguna persona autenticada puede publicar ahí.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('releases', 'releases', true, 52428800, array['application/vnd.android.package-archive'])
on conflict (id) do nothing;
