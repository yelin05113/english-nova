UPDATE users
SET password_hash = '$2a$12$FWhk9zt23JeExTtkJC/Rouzf8czq/zx3/AT5cdP5LFsRTg6BEJfs6'
WHERE id = 1103
  AND username = 'system_public_catalog'
  AND email = 'system-public-catalog@englishnova.local'
  AND password_hash = 'SYSTEM_EXTERNAL_IMPORT';
