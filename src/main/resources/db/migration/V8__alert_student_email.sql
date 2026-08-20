-- V8: store the parent/client email directly on alert_student, copied from Student.email at
-- sync time, instead of resolving it through a live Brevo lookup keyed on VICI_ACCOUNT_ID. That
-- attribute doesn't seem to actually exist on the real Brevo account (unlike CONTACT_TYPE/
-- CONTACT_STATUS/ASSIGNED_TUTOR), which is why the Automations review queue showed every row
-- as "Not Found in Brevo".

alter table alert_student add column email varchar(255);
