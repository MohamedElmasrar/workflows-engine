-- token_hash is always 64 hex characters; varchar matches the JPA String mapping checked by ddl-auto=validate.
alter table webhook_registration alter column token_hash type varchar(64);
