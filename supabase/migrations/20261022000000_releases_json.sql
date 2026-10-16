-- El bucket de releases también aloja `latest.json`, el manifiesto que consulta la app para saber si
-- hay una versión nueva. Sigue siendo de lectura pública; solo se publica con la llave de servicio.
update storage.buckets
   set allowed_mime_types = array['application/vnd.android.package-archive', 'application/json']
 where id = 'releases';
