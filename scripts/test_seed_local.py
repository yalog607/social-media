import unittest
import json
from unittest.mock import patch
import seed_local
from seed_local import ROOT, dataset, schema_guard


class DatasetTest(unittest.TestCase):
    def test_fixture_cannot_mount_main_volume(self):
        info={'State':{'Running':True},'Config':{'Labels':{'aloute.seed.fixture':'true'}},
              'Mounts':[{'Destination':'/var/lib/postgresql/data','Name':'aloute_aloute-pgdata'}]}
        with patch.object(seed_local,'command',return_value=json.dumps([info])):
            with self.assertRaisesRegex(RuntimeError,'fixture identity mismatch'):
                seed_local.validate_target('fixture')

    def test_main_must_match_compose_workspace(self):
        info={'State':{'Running':True},'Config':{'Labels':{'com.docker.compose.project.working_dir':str(ROOT/'wrong'),
                    'com.docker.compose.service':'postgres'}},
              'Mounts':[{'Destination':'/var/lib/postgresql/data','Name':'aloute_aloute-pgdata'}]}
        with patch.object(seed_local,'command',return_value=json.dumps([info])):
            with self.assertRaisesRegex(RuntimeError,'compose directory/service/volume mismatch'):
                seed_local.validate_target('main')

    def test_guard_requires_every_source_migration(self):
        guard=schema_guard('aloute_seed_verify')
        for migration in (ROOT/'src/main/resources/db/migration').glob('V*__*.sql'):
            self.assertIn(migration.name,guard)

    def test_stable_counts_and_synthetic_accounts(self):
        a, b = dataset(), dataset()
        self.assertEqual(a, b)
        for table, count in {'users':20, 'profiles':20, 'user_roles':20, 'posts':40,
                             'comments':30, 'reactions':30, 'conversations':12,
                             'messages':80, 'friendships':20, 'follows':20}.items():
            self.assertEqual(len(a[table]), count, table)
        self.assertTrue(all(u['username'].startswith('seed26_') for u in a['users']))
        self.assertTrue(all(u['role']=='USER' for u in a['user_roles']))

    def test_membership_and_pair_invariants(self):
        d = dataset()
        users = {u['id'] for u in d['users']}
        members = {(m['conversation_id'], m['user_id']) for m in d['conversation_members']}
        self.assertTrue(all((m['conversation_id'],m['sender_id']) in members for m in d['messages']))
        self.assertTrue(all(f['user_a_id'] < f['user_b_id'] for f in d['friendships']))
        self.assertTrue(all(f['follower_id'] in users and f['followee_id'] in users and
                            f['follower_id'] != f['followee_id'] for f in d['follows']))
        for c in d['conversations']:
            if c['type']=='DIRECT':
                ids=sorted(m['user_id'] for m in d['conversation_members'] if m['conversation_id']==c['id'])
                self.assertEqual(len(ids),2)
                self.assertEqual(c['direct_key'],'_'.join(ids))
            else:
                self.assertEqual(sum(m['role']=='OWNER' for m in d['conversation_members'] if m['conversation_id']==c['id']),1)
                owner=c['created_by']
                friends={tuple(sorted([f['user_a_id'],f['user_b_id']])) for f in d['friendships']}
                for m in d['conversation_members']:
                    if m['conversation_id']==c['id'] and m['user_id']!=owner:
                        self.assertIn(tuple(sorted([owner,m['user_id']])),friends)

if __name__ == '__main__':
    unittest.main()
