#!/usr/bin/env python3
"""Calculate a fork version, or reserve a previously calculated version as a tag."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def release_plans():
    plans = []
    for tag in git('tag', '--list', 'fork/*').splitlines():
        try:
            plan = json.loads(git('for-each-ref', '--format=%(contents)', 'refs/tags/' + tag))
        except (ValueError, subprocess.CalledProcessError) as error:
            raise ValueError(f'Invalid fork release tag: {tag}') from error
        if plan.get('schema') != 1 or plan.get('tag') != tag:
            raise ValueError(f'Unrecognized fork release tag: {tag}')
        if git('rev-parse', tag + '^{commit}') != plan['commit']:
            raise ValueError(f'Release tag moved: {tag}')
        plans.append(plan)
    return plans


def calculate(branch):
    if git('rev-parse', '--is-shallow-repository') == 'true':
        raise ValueError('Version calculation requires full Git history.')
    commit = git('rev-parse', 'HEAD')
    channel = 'stable' if branch == 'master' else 'unstable'
    plans = release_plans()
    for plan in plans:
        if plan['commit'] == commit and plan['branch'] == branch:
            return plan

    candidates = []
    for tag in git('tag', '--merged', 'HEAD', '--list', 'upstream/*').splitlines():
        match = re.fullmatch(r'upstream/v?(\d+\.\d+(?:\.\d+){0,2})', tag)
        if match:
            parts = tuple(map(int, match[1].split('.')))
            padded = parts + (0,) * (4 - len(parts))
            candidates.append((padded, tag, parts))
    if not candidates:
        raise ValueError('No reachable upstream release tag. Fetch upstream tags into refs/tags/upstream/* first.')
    _, upstream_tag, parts = max(candidates)
    upstream_version = '.'.join(map(str, parts + (0,) * max(0, 3 - len(parts))))
    releases = [p for p in plans if p['channel'] == 'stable' and p['upstream_version'] == upstream_version]
    if channel == 'unstable':
        # A release on unrelated/newer master history must not change this branch's plan.
        releases = [p for p in releases if subprocess.run(
            ['git', 'merge-base', '--is-ancestor', p['commit'], commit],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0]
    counter = max((p['counter'] for p in releases), default=0) + 1
    version = f'{upstream_version}-ghan1t.{counter}'
    feature = {}
    if channel == 'unstable':
        # CI checks out a detached SHA; prefer the fork's remote master.
        refs = git('for-each-ref', '--format=%(refname)',
                   'refs/remotes/origin/master', 'refs/heads/master').splitlines()
        master = next((ref for ref in ('refs/remotes/origin/master', 'refs/heads/master')
                       if ref in refs), None)
        if master is None:
            raise ValueError('Feature versions require origin/master or a local master branch.')
        branch_base = git('merge-base', master, commit)
        distance = int(git('rev-list', '--count', f'{branch_base}..{commit}'))
        slug = re.sub(r'[^a-z0-9-]+', '-', branch.lower()).strip('-') or 'branch'
        version += f'.{slug}.{distance}'
        # Record how the distance was calculated for the release plan.
        feature = dict(branch_base=branch_base, branch_distance=distance)
    # Total ancestry increases on master without force pushes, even across upstream merges.
    code = 100000 + int(git('rev-list', '--count', 'HEAD'))
    if code > 2100000000:
        raise ValueError('Android versionCode exceeds its supported range.')
    return dict(schema=1, commit=commit, branch=branch, channel=channel,
                upstream_tag=upstream_tag, upstream_version=upstream_version,
                counter=counter, version_name=version, version_code=code,
                tag=f'fork/{channel}/{version}',
                gradle_task='assembleStableRelease' if channel == 'stable' else 'assembleUnstableRelease',
                **feature)


def reserve(plan):
    if plan['commit'] != git('rev-parse', 'HEAD'):
        raise ValueError('The release plan does not describe the checked-out commit.')
    existing = next((p for p in release_plans() if p['tag'] == plan['tag']), None)
    if existing is not None:
        if existing != plan:
            raise ValueError('Version is already reserved for a different plan.')
        return
    # The version job's saved plan is authoritative; do not recalculate it here.
    subprocess.run(['git', 'tag', '-a', plan['tag'], plan['commit'], '-F', '-'],
                   input=json.dumps(plan, sort_keys=True) + '\n', text=True, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--branch')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--reserve', type=Path)
    args = parser.parse_args()
    if args.reserve:
        reserve(json.loads(args.reserve.read_text()))
        return
    plan = calculate(args.branch or git('branch', '--show-current'))
    if not plan['branch']:
        parser.error('--branch is required on a detached checkout')
    serialized = json.dumps(plan, indent=2) + '\n'
    print(serialized, end='')
    if os.environ.get('GITHUB_ACTIONS') == 'true':
        print(f"::notice title=Calculated version::{plan['version_name']}")
    if args.output:
        args.output.write_text(serialized)
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            for key in ('version_name', 'version_code', 'tag', 'channel', 'gradle_task'):
                output.write(f'{key}={plan[key]}\n')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write(f"# {plan['version_name']}\n\nCalculated {plan['channel']} version.\n\n"
                          f"Android versionCode: `{plan['version_code']}`\n\n"
                          f"Upstream base: `{plan['upstream_tag']}`\n\n"
                          f"Commit: `{plan['commit']}`\n\nTag: `{plan['tag']}`\n")


if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
