#!/usr/bin/env bash
#
# Rebuilds the integration branch "developer" from scratch: upstream/main plus every topic
# branch listed below. The branch is generated, never hand-maintained - so a rebased topic
# branch or a squash-merged PR upstream can never leave stale history behind. It is the branch
# the productive jar is built from, and it is never the source of a pull request.
#
# Drop a line from BRANCHES once its PR is merged upstream (the script warns about those), then
# run it again. "git config rerere.enabled true" makes recurring merge conflicts resolve
# themselves from the previous run.
#
# Each entry is resolved as the local branch if it exists, otherwise as origin/<entry>.

set -euo pipefail

BRANCHES=(
    local-only                     # own tooling and workflow tweaks, never submitted upstream
    fix/stale-thing-classes
    fix/increase-decrease-command
    cleanup-code                   # TODO: split into PR-sized topic branches
)

cd "$(dirname "$0")/.."

if ! git diff --quiet HEAD || ! git diff --quiet --cached HEAD; then
    echo "working tree has changes - commit or stash them first" >&2
    exit 1
fi

git fetch upstream --prune
git fetch origin --prune

refs=()
for branch in "${BRANCHES[@]}"; do
    if git rev-parse --verify --quiet "refs/heads/${branch}" > /dev/null; then
        ref="${branch}"
    elif git rev-parse --verify --quiet "refs/remotes/origin/${branch}" > /dev/null; then
        ref="origin/${branch}"
        echo "note: ${branch} exists only on origin, using ${ref}"
    else
        echo "${branch}: no such branch, neither local nor on origin" >&2
        exit 1
    fi

    # A squash merge upstream rewrites the commits, so ancestry says nothing - compare content.
    if git diff --quiet upstream/main "${ref}"; then
        echo "${ref}: already contained in upstream/main, drop it from BRANCHES" >&2
        exit 1
    fi

    refs+=("${ref}")
done

git switch --force-create developer upstream/main
for ref in "${refs[@]}"; do
    echo "merging ${ref}"
    git merge --no-ff -m "Integrate ${ref}" "${ref}"
done

echo
echo "developer rebuilt on $(git rev-parse --short upstream/main) (upstream/main):"
git log --oneline --first-parent upstream/main..developer
echo
echo "next: mvn package, then push with 'git push --force-with-lease origin developer'"
