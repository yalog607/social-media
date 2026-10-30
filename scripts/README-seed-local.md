# Local synthetic seed

Run from the repository root with Python 3 and Docker CLI. No Python packages, application config changes or migrations are installed by these scripts.

```powershell
python scripts/seed_local.py                 # Read-only identity/schema preflight; default target is compose main
python scripts/seed_local.py --apply         # Explicit insert, only when all guards pass
```

Main is fixed to compose container `aloute-postgres`, database/user `aloute`, volume `aloute_aloute-pgdata`, with compose service `postgres` and working directory matching this checkout. The script never starts containers or applies migrations. Every source versioned migration must be present under the same version/filename in successful Flyway history, with no extra/failed versioned migrations; required application columns and the follow-history trigger must exist. This checks migration inventory and columns, not Flyway SQL checksums; application startup still performs its normal Flyway validation. Main V28 currently fails these guards. Do not run migrations on main as part of this seed task.

The approved dataset is **20 new USER accounts**, 40 public text posts, 30 comments (including replies) + 30 post reactions, 10 direct conversations, 2 chat groups, 80 messages, 20 accepted friendships and 20 follows. There are 28 conversation memberships; group members are friends of the group's owner. No social groups, privileged accounts, real media or notifications are fabricated. Follow events are created by the existing database trigger, not inserted by the seed.

Usernames: `seed26_01` through `seed26_20`; emails use `example.invalid`. Public local-only test password: **`SyntheticOnly!2026`**. This is a synthetic credential, never a production password. Display names use `SEED26`, text uses `[SEED26]`, UUIDs are deterministic within this dataset's namespace, and timestamps are fixed for repeatability. Search names/text follow the application's Vietnamese accent normalization. Only seed users are referenced; no existing real/demo users are reused.

Apply runs in one transaction, locks affected tables, compares existing rows at expected keys, and only inserts missing records. It never UPDATEs/DELETEs existing rows or upserts over conflicts. A changed seed row or unique username/email/ID conflict stops and rolls back the transaction. Do not repair conflicts by changing existing data: inspect and resolve ownership/scope separately. Ordinary app-managed fields such as last login/read times are not overwritten. The script may briefly block concurrent writers while its transaction is running; use local development only.

## Isolated verification

```powershell
python -m unittest discover -s scripts -p test_seed_local.py
python scripts/seed_local.py --target fixture
python scripts/seed_local.py --target fixture --apply
python scripts/verify_seed_fixture.py
```

Fixture mode is restricted to container `aloute-seed-verify-20261030`, database `aloute_seed_verify`, database user `aloute`, label `aloute.seed.fixture=true`; it rejects a mount of the main volume. Provision/migrate that empty isolated fixture separately using the checked-out migrations. The verifier only connects to this labelled fixture, adds an unrelated synthetic sentinel, checks repeat/full/nonseed snapshots and trigger/membership invariants, then tests a transaction-local early insert followed by a late conflict and rollback. It does not modify main. The sentinel means total fixture users are 21, of which exactly 20 belong to the seed dataset.

Read-only snapshot commands emit per-table counts/digests, never row contents or password hashes:

```powershell
python scripts/seed_local.py --target fixture --snapshot all
python scripts/seed_local.py --target fixture --snapshot nonseed
```

The retained test app is `http://127.0.0.1:18082`, connecting only to the fixture at port 15440. Browser evidence, runtime command, counts, tests and independent review live in `docs/evidence/local-test-data/`; the task log is `docs/logs/2026-10-30-local-test-data.md`. These `docs/` artifacts are locally ignored by the repository. Login and reading chat history may naturally update authentication/read metadata on the fixture; the preservation/idempotency assertions are measured specifically around seed invocations.
