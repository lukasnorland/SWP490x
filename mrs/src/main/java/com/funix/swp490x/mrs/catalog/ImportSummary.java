package com.funix.swp490x.mrs.catalog;

import java.util.List;

/**
 * What one import did, as reported on P-06c (FT-09 NAC-03).
 *
 * @param listed objects found under the prefix
 * @param read objects whose body was actually downloaded — the rest matched
 *     their stored ETag and were skipped without a read
 * @param added songs created
 * @param updated songs updated in place (DC-04)
 * @param skippedRows rows that were rejected, each with a reason
 * @param alreadyRunning true when another import held the lock and this call
 *     did nothing
 * @param error message when the run failed outright, otherwise null
 */
public record ImportSummary(
        int listed,
        int read,
        int added,
        int updated,
        List<SkippedRow> skippedRows,
        boolean alreadyRunning,
        String error) {

    /** Another import held the lock, so this call did nothing. */
    public static ImportSummary refused() {
        return new ImportSummary(0, 0, 0, 0, List.of(), true, null);
    }

    public int skipped() {
        return skippedRows.size();
    }

    public boolean isFailed() {
        return error != null;
    }

    /** Nothing to do: everything under the prefix was already in step. */
    public boolean isNoChange() {
        return !alreadyRunning && error == null && added == 0 && updated == 0 && skipped() == 0;
    }

    /** One rejected object, named so ADMIN can find and fix it. */
    public record SkippedRow(String key, String reason) {
    }
}
