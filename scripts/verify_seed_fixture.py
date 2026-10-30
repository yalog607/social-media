"""Integration checks against the explicitly labelled seed fixture only."""
import copy
import json
from unittest.mock import patch
import seed_local as seed

container,db=seed.validate_target('fixture')
seed.sql(container,db,seed.schema_guard(db))
manifest=seed.migration_manifest()
before_guard=seed.snapshot(container,db)
with patch.object(seed,'migration_manifest',return_value=manifest+[('999','V999__pending_probe.sql')]):
    try:
        seed.sql(container,db,seed.schema_guard(db))
        raise AssertionError('Pending source migration must be refused')
    except RuntimeError as error:
        assert 'migration manifest differs' in str(error),'Unexpected manifest error'
assert before_guard==seed.snapshot(container,db),'Schema guard modified fixture'
# A pre-existing unrelated synthetic sentinel. Never update/delete it or existing fixture data.
seed.sql(container,db,"""INSERT INTO users (id,username,email,password_hash,status)
    VALUES ('00000000-0000-4000-8000-000000000001','fixture_sentinel','fixture_sentinel@example.invalid',NULL,'ACTIVE')
    ON CONFLICT (id) DO NOTHING;""")
nonseed_before=seed.snapshot(container,db,True)
seed.sql(container,db,seed.seed_sql(db))
first=seed.snapshot(container,db)
seed.sql(container,db,seed.seed_sql(db))
assert first==seed.snapshot(container,db), 'Repeat run changed table contents'
assert nonseed_before==seed.snapshot(container,db,True), 'Nonseed data changed'

# Introduce a transaction-local insert before a late conflict. Connection close must roll it back.
changed=copy.deepcopy(seed.dataset())
changed['comments'][0]['content']='Conflicting expected content'
with patch.object(seed,'dataset',return_value=changed):
    bad_sql=seed.seed_sql(db).replace('BEGIN;', """BEGIN;
        INSERT INTO users(id,username,email,status) VALUES
        ('00000000-0000-4000-8000-000000000002','fixture_rollback_probe','rollback_probe@example.invalid','ACTIVE');""",1)
try:
    seed.sql(container,db,bad_sql)
    raise AssertionError('Expected conflict refusal')
except RuntimeError as error:
    assert 'Seed conflict in comments' in str(error), 'Unexpected SQL error'
assert first==seed.snapshot(container,db), 'Conflict was not atomic'

integrity=seed.sql(container,db,"""
SELECT count(*) FROM messages m LEFT JOIN conversation_members cm
 ON cm.conversation_id=m.conversation_id AND cm.user_id=m.sender_id WHERE cm.id IS NULL;
SELECT count(*) FROM follows f LEFT JOIN follow_events e ON e.id=f.id
 WHERE e.id IS NULL OR e.follower_id<>f.follower_id OR e.followee_id<>f.followee_id;
SELECT count(*) FROM follow_events;
""").splitlines()
assert integrity==['0','0','20'],integrity
print(json.dumps({'idempotent_full_snapshot':True,'nonseed_unchanged':True,'late_conflict_rolled_back':True,
                  'pending_migration_refused_without_changes':True,
                  'sender_membership_valid':True,'follow_trigger_events':20,
                  'snapshot':first},sort_keys=True))
