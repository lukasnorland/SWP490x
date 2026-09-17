# Search interaction and test cases

P-02 uses a playlist prompt, an optional Top-N field and Interpret. Results and
removable chips appear immediately after interpretation. There is no metadata
filter panel, Apply/Clear form, or separate Search button on this page.
Manual metadata filtering stays on Songs (P-08) and the admin catalog (P-06b).

This decision restores the interaction introduced in `6b1d4e5` and supersedes
the P-02 panel added in `966e28d` to follow the older test design. It applies to
UC-10/UC-11 and the P-02 layout in Report 3.2. The filter parameters in search
URLs remain the internal representation of interpreted criteria, shared by
paging, chip removal, preview and create-from-results.

## Behaviour

- Enter 10–200 characters and optionally a positive integer Top-N, then Interpret.
- Results match any interpreted chip; more matching chips rank first, followed
  by title and id. Catalog browsing retains its own category intersection rules.
- Remove an existing chip to query the remaining criteria without a new
  interpretation or recommendation log entry. To add or replace criteria,
  change the prompt and Interpret again; that submission creates a new log entry.
- Top-N is beside Interpret. Blank means all matches. A value of 0 or a negative
  integer returns HTTP 422 on GET `/search` and POST `/search/interpret`, shows
  an inline error, and does not run interpretation or a result query.
- A first visit is empty. No matches shows MSG_006 and suggests removing a chip
  or broadening the prompt. The prompt remains available.

## Integration test updates

Use an isolated catalog and a deterministic interpreter or offline vocabulary
matching for exact result counts. Live Gemini tests verify interpretation and
logging separately; they must not assume a model always returns the same chips.

| Case | Procedure and expected result |
| --- | --- |
| SRC-IT-01 | Open Search and verify the prompt/Top-N/Interpret controls and absence of metadata dropdowns. Interpret a fixture prompt mapped to Pop and Happy. Include songs with either chip; songs with both rank ahead of partial matches. |
| SRC-IT-02 | Interpret a fixture prompt mapped to a known tag with no matching songs. Show MSG_006 and zero rows. Two tags with no intersection are not a valid no-results fixture for any-chip search. |
| SRC-IT-03 | Interpret a fixture prompt with exactly 21 matches and blank Top-N. Page 1 has 20 songs, page 2 has one, with no duplicates or new interpretation. |
| SRC-IT-04 | Interpret a fixture prompt mapped to Pop, Happy and Summer. Verify ranking by chip count, title and id, including partial matches. |
| SRC-IT-05 | Use a fixture prompt with eight matches. Submit Top-N 0, -1, 1 and 20. Invalid values show the error under the prompt's Top-N field and invoke no interpreter/query. Valid values return one and eight songs. Bypass HTML validation with a direct form POST for invalid-value server checks. |
| SRC-IT-06 | Interpret a contextual prompt. Chips and results appear immediately; verify the result set/order against the metadata engine for those chips and verify the original recommendation log. No second Search action is required. |
| SRC-IT-09 | Interpret a prompt and remove one chip. Results use the remaining chips; the original log is unchanged. Revise the prompt to request Pop and Interpret again; verify the new criteria and a new log entry. |

SRC-IT-07/08 (query length and fallback), SRC-IT-10 (playlist creation), and
SRC-IT-11 (Songs and permissions) keep their existing objectives. A test that
needs results should obtain them through Interpret rather than a removed control.
Report 5.1 service-level cases remain applicable; removing the panel does not
remove the metadata engine or its filter parameters.

## Verification

Run `SearchFlowTest`, `SearchServiceTest`, `ScreenRenderingTest`, `SongBrowseTest`
and `AdminCatalogImportTest`. Report 5.2 procedures are design updates, not proof
of a completed browser run. Re-run the revised cases before assigning new
integration-test results. Historical evidence from the panel UI remains historical.

The Word and Excel reports under `docs/` are local-only files, ignored by Git.
This tracked note records the interaction decision for future implementation
and test maintenance.
