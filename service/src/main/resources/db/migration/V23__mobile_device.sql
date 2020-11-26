CREATE TABLE mobile_device(
    id uuid PRIMARY KEY REFERENCES employee(id),
    created timestamp with time zone DEFAULT now() NOT NULL,
    updated timestamp with time zone DEFAULT now() NOT NULL,
    unit_id uuid NOT NULL REFERENCES daycare(id),
    name text NOT NULL,
    deleted boolean NOT NULL DEFAULT false
);

CREATE TRIGGER set_timestamp BEFORE UPDATE ON mobile_device FOR EACH ROW EXECUTE PROCEDURE trigger_refresh_updated();
