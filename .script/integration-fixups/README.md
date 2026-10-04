# Integration fixups

Patches applied by `rebuild-developer.sh` after all topic branches are merged.

They exist for *semantic* conflicts only: two topic branches that merge cleanly but do not
compile together, so neither branch can carry the fix without breaking its own pull request.
Textual conflicts do not belong here - `git rerere` replays those on its own.

Each fixup dies with the branch pair that caused it. When a listed branch is merged upstream or
split up, delete the patch along with it.

- `0001-increase-decrease-test-vs-static-toValue.patch`: `cleanup-code` drops the
  `JRuleEventHandler.get()` singleton and turns `toValue` static, while the test from
  `fix/increase-decrease-command` calls it through `get()` - that branch targets upstream, where
  the method is still an instance method.
