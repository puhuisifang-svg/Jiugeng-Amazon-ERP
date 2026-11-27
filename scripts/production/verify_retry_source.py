"""Allow documentation-only advancement, never retry superseded application code."""
import subprocess
import sys


def require_current_application(revision, main='origin/main', *, cwd=None):
    subprocess.run(['git', 'merge-base', '--is-ancestor', revision, main], cwd=cwd, check=True)
    result = subprocess.run(['git', 'diff', '--quiet', revision, main, '--',
                             'frontend', 'deliverables/backend', 'deliverables/pm/excel_templates'],
                            cwd=cwd)
    if result.returncode != 0:
        raise ValueError('Application source changed after this package; retry the newer tested release')


if __name__ == '__main__':
    require_current_application(sys.argv[1])
