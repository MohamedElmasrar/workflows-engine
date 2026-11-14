-- One PostgreSQL server, one database and one role per service (development topology).
-- Services never share a database; each role owns only its own database.
create role workflow  login password 'workflow';
create role execution login password 'execution';
create role trigger_svc login password 'trigger';
create role keycloak  login password 'keycloak';

create database workflow  owner workflow;
create database execution owner execution;
create database trigger   owner trigger_svc;
create database keycloak  owner keycloak;

revoke all on database workflow  from public;
revoke all on database execution from public;
revoke all on database trigger   from public;
revoke all on database keycloak  from public;
