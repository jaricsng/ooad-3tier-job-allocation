#!/usr/bin/env bash
# Apply the repository's GitHub security and branch settings (idempotent). Needs the GitHub CLI, signed in:
#   gh auth login
#   scripts/github-settings.sh OWNER/REPO
# Run it after the first push, so that branch main and the ci workflow exist.
set -euo pipefail
repo="${1:?usage: $0 OWNER/REPO}"

echo "Secret scanning and push protection"
gh api -X PATCH "repos/$repo" --silent \
  -f 'security_and_analysis[secret_scanning][status]=enabled' \
  -f 'security_and_analysis[secret_scanning_push_protection][status]=enabled'

echo "Dependabot alerts and security updates"
gh api -X PUT "repos/$repo/vulnerability-alerts" --silent
gh api -X PUT "repos/$repo/automated-security-fixes" --silent

echo "Private vulnerability reporting"
gh api -X PUT "repos/$repo/private-vulnerability-reporting" --silent

echo "Merge settings: squash only, delete branches after merge"
gh api -X PATCH "repos/$repo" --silent -F allow_merge_commit=false -F allow_rebase_merge=false \
  -F allow_squash_merge=true -F delete_branch_on_merge=true

echo "Protect main: pull request, passing 'check' and 'check-latest-jdk', up to date, linear history, no force push"
# Approvals: 0 while you are the only maintainer (GitHub does not let authors approve their own pull request).
# Set APPROVALS=1 when a co-maintainer or teaching assistant can review.
gh api -X PUT "repos/$repo/branches/main/protection" --silent --input - <<JSON
{
  "required_status_checks": { "strict": true, "contexts": ["check", "check-latest-jdk"] },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": ${APPROVALS:-0},
    "dismiss_stale_reviews": true,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
echo "Done: https://github.com/$repo/settings"
