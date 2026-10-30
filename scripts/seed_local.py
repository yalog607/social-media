"""Add-only synthetic local data. Python stdlib + Docker; never migrates a database."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import unicodedata
import uuid

ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = uuid.UUID('c59d0065-3b3d-4b27-a082-27657a1d0867')
STAMP = '2026-10-30T00:00:00Z'
# Public synthetic credential only: SyntheticOnly!2026. Generated with the project's BCryptPasswordEncoder.
PASSWORD_HASH = '$2a$10$A6/S1QTnLsuXa3eolrV8JeoMtOsdb1W0goWjwXmwG1rdMY9Ii588u'


def ident(kind, number):
    return str(uuid.uuid5(NAMESPACE, f'aloute-local-seed-v1:{kind}:{number}'))


def normalize(text):
    return ''.join(c for c in unicodedata.normalize('NFD', text.replace('đ','d').replace('Đ','D'))
                   if not unicodedata.combining(c)).lower()


def dataset():
    data = {t: [] for t in ('users','profiles','user_roles','posts','comments','reactions',
                            'friendships','follows','conversations','conversation_members','messages')}
    uid = lambda n: ident('user', n % 20)
    for i in range(20):
        username=f'seed26_{i+1:02}'
        name=f'SEED26 Bạn thử {i+1:02}'
        data['users'].append(dict(id=uid(i),username=username,email=f'{username}@example.invalid',
                                 password_hash=PASSWORD_HASH,auth_provider='LOCAL',status='ACTIVE',
                                 email_verified=True,created_at=STAMP))
        data['profiles'].append(dict(user_id=uid(i),display_name=name,
                                    search_name=normalize(username+' '+name),profile_visibility='PUBLIC',
                                    default_post_visibility='PUBLIC',message_permission='EVERYONE'))
        data['user_roles'].append(dict(user_id=uid(i),role='USER'))
        data['follows'].append(dict(id=ident('follow',i),follower_id=uid(i),followee_id=uid(i+1),created_at=STAMP))
    pairs=[(i,i+1) for i in range(16)]+[(0,2),(0,3),(4,6),(4,7)]
    for i,(left,right) in enumerate(pairs):
        a,b=sorted([uid(left),uid(right)])
        data['friendships'].append(dict(id=ident('friend',i),user_a_id=a,user_b_id=b,status='ACCEPTED',
                                       requested_by=uid(left),created_at=STAMP,responded_at=STAMP))
    for i in range(40):
        content=f'[SEED26] Bài thử {i+1:02}: học tập, âm nhạc và ngày mới cùng bạn bè.'
        data['posts'].append(dict(id=ident('post',i),author_id=uid(i),content=content,
                                 search_text=normalize(content),visibility='PUBLIC',created_at=STAMP,updated_at=STAMP))
    for i in range(30):
        data['comments'].append(dict(id=ident('comment',i),post_id=ident('post',i%10),
                                    author_id=uid(i+1),parent_id=None if i<20 else ident('comment',i-20),
                                    content=f'[SEED26] Bình luận thử {i+1:02} — cùng trao đổi nhé!',created_at=STAMP))
        data['reactions'].append(dict(id=ident('reaction',i),post_id=ident('post',i),user_id=uid(i+2),
                                     type=['LOVE','HAHA','FIRE','WOW','SAD'][i%5],created_at=STAMP))
    member_ids={}
    for i in range(12):
        members=[uid(2*i),uid(2*i+1)] if i<10 else [uid(0),uid(1),uid(2),uid(3)] if i==10 else [uid(4),uid(5),uid(6),uid(7)]
        member_ids[i]=members
        cid=ident('conversation',i)
        data['conversations'].append(dict(id=cid,type='DIRECT' if i<10 else 'GROUP',
                                         title=None if i<10 else f'[SEED26] Nhóm thử {i-9}',
                                         direct_key='_'.join(sorted(members)) if i<10 else None,
                                         created_by=members[0],created_at=STAMP,is_social_group=False,
                                         chat_enabled=True,members_can_send_messages=True))
        for j,member in enumerate(members):
            data['conversation_members'].append(dict(id=ident('member',f'{i}:{j}'),conversation_id=cid,
                                                     user_id=member,role='OWNER' if i>=10 and j==0 else 'MEMBER',
                                                     joined_at=STAMP))
    for i in range(80):
        c=i%12
        data['messages'].append(dict(id=ident('message',i),conversation_id=ident('conversation',c),
                                    sender_id=member_ids[c][(i//12)%len(member_ids[c])],
                                    content=f'[SEED26] Tin nhắn thử {i+1:02}: xin chào hội bạn!',created_at=STAMP,is_system=False))
    return data


def command(args, stdin=None):
    p=subprocess.run(args,input=stdin,text=True,encoding='utf-8',capture_output=True,cwd=ROOT)
    if p.returncode:
        # Docker/SQL errors are useful; never print submitted SQL or password hashes.
        raise RuntimeError(p.stderr.strip() or 'Command failed')
    return p.stdout.strip()


def validate_target(target):
    container,db=('aloute-postgres','aloute') if target=='main' else ('aloute-seed-verify-20261030','aloute_seed_verify')
    info=json.loads(command(['docker','inspect',container]))[0]
    labels=info['Config'].get('Labels') or {}
    if not info['State']['Running']:
        raise RuntimeError('Target container is not running')
    volumes=[m.get('Name') for m in info['Mounts'] if m['Destination']=='/var/lib/postgresql/data']
    if target=='main':
        directory=labels.get('com.docker.compose.project.working_dir','')
        if (Path(directory).resolve()!=ROOT or labels.get('com.docker.compose.service')!='postgres'
                or volumes!=['aloute_aloute-pgdata']):
            raise RuntimeError('Refusing target: compose directory/service/volume mismatch')
    elif labels.get('aloute.seed.fixture')!='true' or 'aloute_aloute-pgdata' in volumes:
        raise RuntimeError('Refusing target: isolated fixture identity mismatch')
    return container,db


def sql(container,db,statement):
    return command(['docker','exec','-i',container,'psql','-U','aloute','-d',db,
                    '-X','-q','-A','-t','-v','ON_ERROR_STOP=1'],statement)


def literal(value):
    if value is None: return 'NULL'
    if isinstance(value,bool): return 'TRUE' if value else 'FALSE'
    return "'"+str(value).replace("'","''")+"'"


def migration_manifest():
    return sorted((p.name.split('__',1)[0][1:],p.name)
                  for p in (ROOT/'src/main/resources/db/migration').glob('V*__*.sql'))


def schema_guard(db):
    manifest=migration_manifest()
    if not manifest: raise RuntimeError('No source migration manifest found')
    expected=','.join('('+literal(version)+','+literal(script)+')' for version,script in manifest)
    return """
DO $guard$
BEGIN
    IF current_database() <> %s THEN RAISE EXCEPTION 'Wrong database'; END IF;
    IF EXISTS (SELECT FROM flyway_schema_history WHERE NOT success)
       OR EXISTS (SELECT FROM (VALUES %s) expected(version,script)
                  WHERE NOT EXISTS (SELECT FROM flyway_schema_history actual
                    WHERE actual.version=expected.version AND actual.script=expected.script AND actual.success))
       OR EXISTS (SELECT FROM flyway_schema_history actual WHERE actual.version IS NOT NULL
                  AND NOT EXISTS (SELECT FROM (VALUES %s) expected(version,script)
                    WHERE actual.version=expected.version AND actual.script=expected.script)) THEN
        RAISE EXCEPTION 'Schema migration manifest differs from source (pending/extra/failed migration); script never migrates';
    END IF;
    IF EXISTS (SELECT 1 FROM (VALUES ('profiles','avatar_frame'),('messages','sticker_id'),
        ('conversations','is_social_group'),('conversations','chat_enabled'),
        ('conversations','members_can_send_messages'),('posts','group_id')) AS required(t,c)
        WHERE NOT EXISTS (SELECT FROM information_schema.columns actual WHERE actual.table_schema='public'
            AND actual.table_name=required.t AND actual.column_name=required.c)) THEN
        RAISE EXCEPTION 'Schema missing current application columns; no writes performed';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_trigger WHERE tgrelid='follows'::regclass
                   AND tgname='follows_record_event' AND tgenabled='O') THEN
        RAISE EXCEPTION 'Required follow-history trigger is missing/disabled';
    END IF;
END $guard$;
""" % (literal(db),expected,expected)


def seed_sql(db):
    data=dataset()
    pieces=['BEGIN;',schema_guard(db),
            'LOCK TABLE '+','.join('public.'+t for t in data)+',public.follow_events IN SHARE ROW EXCLUSIVE MODE;']
    for table,rows in data.items():
        cols=list(rows[0])
        columns=','.join(cols)
        values=','.join('('+','.join(literal(r[c]) for c in cols)+')' for r in rows)
        key='user_id' if table in ('profiles','user_roles') else 'id'
        pieces.extend([
            f'CREATE TEMP TABLE expected_{table} (LIKE public.{table} INCLUDING DEFAULTS) ON COMMIT DROP;',
            f'INSERT INTO expected_{table} ({columns}) VALUES {values};',
            f"""DO $conflict$ BEGIN
            IF EXISTS (SELECT FROM public.{table} a JOIN expected_{table} e ON a.{key}=e.{key}
                WHERE ROW({','.join('a.'+c for c in cols)}) IS DISTINCT FROM ROW({','.join('e.'+c for c in cols)})) THEN
                RAISE EXCEPTION 'Seed conflict in {table}; transaction rolled back, existing data never overwritten';
            END IF; END $conflict$;""",
            # Other unique collisions intentionally fail, rolling back the entire transaction.
            f'INSERT INTO public.{table} ({columns}) SELECT '+','.join('e.'+c for c in cols)+
            f' FROM expected_{table} e WHERE NOT EXISTS (SELECT FROM public.{table} a WHERE a.{key}=e.{key});'
        ])
    pieces.append('COMMIT;')
    return '\n'.join(pieces)


def snapshot(container,db,nonseed=False):
    data=dataset()
    tables=sql(container,db,"SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;").splitlines()
    queries=[]
    for table in tables:
        if not table.replace('_','').isalnum(): raise RuntimeError('Unexpected table identifier')
        condition=''
        rows=data.get(table, data['follows'] if table=='follow_events' else None)
        if nonseed and rows:
            key='user_id' if table in ('profiles','user_roles') else 'id'
            condition=' WHERE '+key+' NOT IN ('+','.join(literal(r[key]) for r in rows)+')'
        queries.append(f"SELECT '{table}|' || count(*) || ':' || md5(coalesce(string_agg(to_jsonb(t)::text,E'\\n' ORDER BY to_jsonb(t)::text),'')) FROM public.{table} t"+condition+';')
    return dict(line.split('|',1) for line in sql(container,db,'\n'.join(queries)).splitlines())


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',choices=['main','fixture'],default='main')
    parser.add_argument('--apply',action='store_true',help='Insert only after identity and schema checks pass')
    parser.add_argument('--snapshot',choices=['all','nonseed'],help='Read-only per-table counts/digests, no row data')
    args=parser.parse_args()
    container,db=validate_target(args.target)
    if args.snapshot:
        print(json.dumps(snapshot(container,db,args.snapshot=='nonseed'),sort_keys=True)); return
    if not args.apply:
        sql(container,db,schema_guard(db))
        print(json.dumps({'target':container,'database':db,'schema':'ready','mode':'check-only'})); return
    sql(container,db,seed_sql(db))
    counts={}
    for table,rows in dataset().items():
        key='user_id' if table in ('profiles','user_roles') else 'id'
        counts[table]=int(sql(container,db,f'SELECT count(*) FROM public.{table} WHERE {key} IN ('+
                              ','.join(literal(r[key]) for r in rows)+');'))
    print(json.dumps({'target':container,'database':db,'counts':counts},sort_keys=True))


if __name__=='__main__':
    try: main()
    except (RuntimeError,ValueError,KeyError) as error:
        print('REFUSED: '+str(error),file=sys.stderr)
        sys.exit(1)
