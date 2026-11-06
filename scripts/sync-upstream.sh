#!/usr/bin/env bash
#
# Pull the newest upstream MCA Selector core into the Plus branch.
#
#   master : untouched mirror of Querz/mcaselector, never contains Plus commits
#   plus   : the extension layer, always a linear set of commits on top of master
#
# The core version in gradle.properties comes from upstream and is overwritten on every sync.
# plus.version is owned by this repository and is never touched here.
#
# Set UPSTREAM_URL to fetch through another route (ssh alias, mirror):
#   UPSTREAM_URL=git@github.com-me:Querz/mcaselector.git scripts/sync-upstream.sh
#
set -euo pipefail

UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/Querz/mcaselector.git}"
CORE_BRANCH="${CORE_BRANCH:-master}"
PLUS_BRANCH="${PLUS_BRANCH:-plus}"

die() { echo "error: $*" >&2; exit 1; }

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "run this from inside the repository"
[ -z "$(git status --porcelain)" ] || die "working tree is dirty, commit or stash first"

# fetch by url into the upstream tracking refs: the remote configuration of the clone stays as it is
echo "==> fetching upstream ($UPSTREAM_URL)"
git fetch "$UPSTREAM_URL" \
	"+$CORE_BRANCH:refs/remotes/upstream/$CORE_BRANCH" \
	"+refs/tags/*:refs/tags/*" \
	--prune

# a fork tracks upstream/master and origin/master at once, a bare checkout would not know which one is meant
echo "==> moving $CORE_BRANCH to upstream/$CORE_BRANCH"
git checkout -B "$CORE_BRANCH" "upstream/$CORE_BRANCH"

echo "==> rebasing $PLUS_BRANCH onto $CORE_BRANCH"
git checkout -B "$PLUS_BRANCH" "origin/$PLUS_BRANCH"
if ! git rebase "$CORE_BRANCH"; then
	cat <<-'EOF'

	The rebase stopped: the upstream core touched code that the Plus layer also changes.
	Resolve the conflicts, then continue:

	    git add <files> && git rebase --continue

	Give up and return to the previous state with:

	    git rebase --abort
	EOF
	exit 1
fi

# grep instead of sed: gradle.properties carries a copyright sign and BSD sed trips over it
CORE_VERSION=$(grep -m1 '^version=' gradle.properties | cut -d= -f2- | tr -d '\r')
PLUS_VERSION=$(grep -m1 '^plus.version=' gradle.properties | cut -d= -f2- | tr -d '\r')

echo
echo "core version (from upstream): $CORE_VERSION"
echo "plus version (this repo)    : $PLUS_VERSION"
echo "display version             : MCA Selector $CORE_VERSION - Plus $PLUS_VERSION"
echo
echo "next: ./gradlew test shadowJar, bump plus.version if the extension changed, then git push origin $CORE_BRANCH $PLUS_BRANCH"
