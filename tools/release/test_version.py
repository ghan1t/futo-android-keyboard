import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('version.py').resolve()


class VersionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name)
        self.git('init', '-b', 'master')
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.commit()
        self.git('tag', 'upstream/0.1.30')

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, text=True, stderr=subprocess.DEVNULL).strip()

    def commit(self):
        self.git('commit', '--allow-empty', '-m', 'change')

    def plan(self, branch='master', success=True):
        result = subprocess.run(['python3', str(SCRIPT), '--branch', branch], cwd=self.repo,
                                capture_output=True, text=True,
                                env={k: v for k, v in os.environ.items() if not k.startswith('GITHUB_')})
        if not success:
            self.assertNotEqual(result.returncode, 0)
            return result.stderr
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def reserve(self, plan):
        path = self.repo / 'plan.json'
        path.write_text(json.dumps(plan))
        return subprocess.run(['python3', str(SCRIPT), '--reserve', str(path)], cwd=self.repo,
                              capture_output=True, text=True)

    def test_tag_reserves_version_and_retry_reuses_it(self):
        self.commit()
        first = self.plan()
        self.assertEqual(first['version_name'], '0.1.30-ghan1t.1')
        self.assertEqual(first, self.plan())
        self.assertEqual(self.reserve(first).returncode, 0)
        self.assertEqual(first, self.plan())
        self.assertEqual(self.reserve(first).returncode, 0)
        self.commit()
        second = self.plan()
        self.assertEqual(second['version_name'], '0.1.30-ghan1t.2')
        self.assertGreater(second['version_code'], first['version_code'])

    def test_upstream_change_resets_suffix_not_android_code(self):
        first = self.plan()
        self.reserve(first)
        self.commit()
        self.git('tag', 'upstream/0.1.31')
        second = self.plan()
        self.assertEqual(second['version_name'], '0.1.31-ghan1t.1')
        self.assertGreater(second['version_code'], first['version_code'])

    def test_missing_patch_and_four_part_versions(self):
        self.git('tag', '-d', 'upstream/0.1.30')
        self.git('tag', 'upstream/v0.2')
        self.assertEqual(self.plan()['version_name'], '0.2.0-ghan1t.1')
        self.commit()
        self.git('tag', 'upstream/0.2.0.1')
        self.assertEqual(self.plan()['version_name'], '0.2.0.1-ghan1t.1')

    def test_unmerged_and_prerelease_upstream_tags_are_ignored(self):
        self.git('tag', 'upstream/9.0.0-rc1')
        self.git('checkout', '-b', 'upstream-next')
        self.commit()
        self.git('tag', 'upstream/9.0.0')
        self.git('checkout', 'master')
        self.assertEqual(self.plan()['upstream_version'], '0.1.30')

    def test_features_are_distinct_and_do_not_increment_stable_counter(self):
        self.commit()
        a = self.plan('feat/first')
        b = self.plan('feat/second')
        self.assertNotEqual(a['tag'], b['tag'])
        self.assertEqual(a['channel'], 'unstable')
        self.assertEqual(self.reserve(a).returncode, 0)
        self.assertEqual(a, self.plan('feat/first'))
        self.assertEqual(self.plan()['counter'], 1)

    def test_unrelated_master_release_does_not_change_feature_preview(self):
        self.git('checkout', '-b', 'feat/test')
        self.commit()
        feature = self.plan('feat/test')
        self.git('checkout', 'master')
        self.git('commit', '--allow-empty', '-m', 'master-only')
        self.assertEqual(self.reserve(self.plan()).returncode, 0)
        self.git('checkout', 'feat/test')
        self.assertEqual(feature, self.plan('feat/test'))

    def test_reserved_plan_survives_new_upstream_tag_on_same_commit(self):
        self.commit()
        first = self.plan()
        self.assertEqual(self.reserve(first).returncode, 0)
        self.git('tag', 'upstream/0.1.31')
        self.assertEqual(first, self.plan())

    def test_reservation_uses_saved_plan_without_upstream_tags(self):
        plan = self.plan()
        self.git('tag', '-d', 'upstream/0.1.30')
        self.assertEqual(self.reserve(plan).returncode, 0)
        self.assertEqual(json.loads(self.git('for-each-ref', '--format=%(contents)',
                                            'refs/tags/' + plan['tag'])), plan)

    def test_upstream_change_between_jobs_does_not_change_reservation(self):
        plan = self.plan()
        self.git('tag', 'upstream/0.1.31')
        self.assertEqual(self.reserve(plan).returncode, 0)
        self.assertEqual(plan, self.plan())

    def test_feature_distance_starts_at_fork_master_not_upstream(self):
        self.commit()
        self.commit()
        base = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-b', 'feat/example')
        self.commit()
        self.commit()
        plan = self.plan('feat/example')
        self.assertEqual(plan['version_name'], '0.1.30-ghan1t.1.feat-example.2')
        self.assertEqual(plan['tag'], 'fork/unstable/' + plan['version_name'])
        self.assertEqual(plan['branch_base'], base)
        self.assertEqual(self.reserve(plan).returncode, 0)
        self.assertEqual(plan, self.plan('feat/example'))

    def test_detached_checkout_prefers_remote_master_and_freezes_base(self):
        self.commit()
        base = self.git('rev-parse', 'HEAD')
        self.git('update-ref', 'refs/remotes/origin/master', base)
        self.git('checkout', '--detach')
        self.git('branch', '-f', 'master', 'HEAD~1')
        self.commit()
        plan = self.plan('feat/example')
        self.assertEqual(plan['version_name'], '0.1.30-ghan1t.1.feat-example.1')
        self.assertEqual(plan['branch_base'], base)
        # Master catches up between calculation and reservation.
        self.git('update-ref', 'refs/remotes/origin/master', 'HEAD')
        self.assertEqual(self.reserve(plan).returncode, 0)
        self.assertEqual(plan, self.plan('feat/example'))

    def test_rewritten_feature_cannot_overwrite_equal_distance_reservation(self):
        self.git('checkout', '-b', 'feat/example')
        base = self.git('rev-parse', 'HEAD')
        self.git('commit', '--allow-empty', '-m', 'first candidate')
        first = self.plan('feat/example')
        self.assertEqual(first['version_name'], '0.1.30-ghan1t.1.feat-example.1')
        self.assertEqual(self.reserve(first).returncode, 0)
        self.git('checkout', '-b', 'alternate', base)
        self.git('commit', '--allow-empty', '-m', 'second candidate')
        second = self.plan('feat/example')
        self.assertEqual(first['version_code'], second['version_code'])
        self.assertEqual(first['version_name'], second['version_name'])
        self.assertEqual(first['tag'], second['tag'])
        self.assertIn('already reserved', self.reserve(second).stderr)

    def test_legacy_feature_reservation_keeps_original_version(self):
        old = self.plan('feat/example')
        old['version_name'] = '0.1.30-ghan1t.1.feat-example-12345678.0+g' + old['commit'][:12]
        old['tag'] = 'fork/unstable/' + old['version_name']
        self.git('tag', '-a', old['tag'], '-m', json.dumps(old))
        self.assertEqual(old, self.plan('feat/example'))
        self.assertEqual(self.reserve(old).returncode, 0)

    def test_github_summary_outputs_and_notice(self):
        summary = self.repo / 'summary.md'
        output = self.repo / 'outputs.txt'
        plan_file = self.repo / 'version.json'
        env = {k: v for k, v in os.environ.items() if not k.startswith('GITHUB_')}
        env.update(GITHUB_ACTIONS='true', GITHUB_STEP_SUMMARY=str(summary), GITHUB_OUTPUT=str(output))
        result = subprocess.run(['python3', str(SCRIPT), '--branch', 'master',
                                 '--output', str(plan_file)], cwd=self.repo,
                                env=env, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        version = json.loads(plan_file.read_text())['version_name']
        self.assertTrue(summary.read_text().startswith('# ' + version + '\n'))
        self.assertIn('version_name=' + version + '\n', output.read_text())
        self.assertIn('::notice title=Calculated version::' + version, result.stdout)

    def test_stale_plan_cannot_tag_different_commit(self):
        plan = self.plan()
        self.commit()
        self.assertNotEqual(self.reserve(plan).returncode, 0)

    def test_no_upstream_tag_fails_instead_of_publishing_zero_version(self):
        self.git('tag', '-d', 'upstream/0.1.30')
        self.assertIn('No reachable upstream', self.plan(success=False))


if __name__ == '__main__':
    unittest.main()
