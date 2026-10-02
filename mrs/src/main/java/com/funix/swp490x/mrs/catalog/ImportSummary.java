package com.funix.swp490x.mrs.catalog;

import java.util.List;

/**
 * Catalog import counts and rejection reasons for P-06b (UC-28).
 * @param listed staged objects found
 * @param read object bodies downloaded
 * @param added songs created
 * @param updated songs updated
 * @param removed songs pruned after staging deletion
 * @param skippedRows rejected entries and reasons
 * @param alreadyRunning true if this call was refused because another import is running
 * @param error failure message, or null
 */
public record ImportSummary(
        int listed,
        int read,
        int added,
        int updated,
        int removed,
        List<SkippedRow> skippedRows,
        boolean alreadyRunning,
        String error) {

    /** Another import held the lock, so this call did nothing. */
    public static ImportSummary refused() {
        return new ImportSummary(0, 0, 0, 0, 0, List.of(), true, null);
    }

    public int skipped() {
        return skippedRows.size();
    }

    public boolean isFailed() {
        return error != null;
    }

    /** Nothing to do: everything under the prefix was already in step. */
    public boolean isNoChange() {
        return !alreadyRunning && error == null && added == 0 && updated == 0
                && removed == 0 && skipped() == 0;
    }

    /** One rejected object, named so ADMIN can find and fix it. */
    public record SkippedRow(String key, String reason) {
    }
}
