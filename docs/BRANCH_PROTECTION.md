# Branch protection for `main`

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

Living Realms expects `main` to be merge-gated. If repository administration rights are unavailable from the agent environment, apply the following manually in GitHub:

## Desired ruleset / classic branch protection

- Require a pull request before merging
- Require status checks to pass:
  - `core`
  - `linked-build`
  - `gametest` (when enabled in CI)
- Require branches to be up to date before merging
- Block force pushes
- Block branch deletion
- Do **not** require external approving reviews for a one-person project (optional)

## Example GitHub CLI (admin token required)

```bash
gh api repos/syneyexx/Mc-LivingRealms-Mod/branches/main/protection \
  -X PUT \
  -H "Accept: application/vnd.github+json" \
  -f required_status_checks='{"strict":true,"contexts":["core","linked-build"]}' \
  -F enforce_admins=false \
  -F required_pull_request_reviews='{"required_approving_review_count":0}' \
  -F restrictions='' \
  -F allow_force_pushes=false \
  -F allow_deletions=false
```

If this file is present but protection is not configured, treat branch hardening as an external admin gate — do not claim it was enabled.
