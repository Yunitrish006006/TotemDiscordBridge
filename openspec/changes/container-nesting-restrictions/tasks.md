# Tasks: Container Nesting Restrictions

## Policy

- [x] Add a central portable-container classification service.
- [x] Add predicates for Bundle, Shulker Box and DeadRecall backpack containers.
- [x] Implement a bidirectional denied insertion matrix.
- [ ] Add data-driven tags for addon portable containers.

## Integration

- [x] Enforce the policy in DeadRecall backpack slots and quick-move logic.
- [ ] Enforce the policy in Bundle insertion paths.
- [ ] Enforce the policy in Shulker Box and generic container transfer paths.
- [ ] Cover hopper, hopper minecart, dropper and dispenser transfers.
- [x] Apply the same rule to death capture and transient-stack fallback.
- [ ] Verify recovery and rollback exactly-once behavior with restricted containers.
- [ ] Add localized rejection messages and rate-limited automation diagnostics.

## Legacy data

- [x] Preserve existing nested contents on load.
- [x] Allow extracting invalid nested items from DeadRecall backpacks.
- [x] Reject reinsertion without deleting or rewriting the stack in DeadRecall backpack menus.
- [ ] Add an admin diagnostic command or report for invalid nesting.

## Tests

- [ ] Backpack into Bundle and Bundle into backpack.
- [ ] Backpack into every Shulker Box color and reverse direction.
- [ ] Drag, shift-click, number-key, double-click and cursor tests.
- [ ] Hopper, hopper minecart, dropper and dispenser tests.
- [ ] Death capture and rollback exactly-once tests for Bundle and Shulker Box stacks.
- [ ] Existing invalid nesting load/extract/reinsert tests.
- [ ] Custom Data Components and named container preservation tests.
- [ ] Multiplayer race and Dedicated Server restart tests.
