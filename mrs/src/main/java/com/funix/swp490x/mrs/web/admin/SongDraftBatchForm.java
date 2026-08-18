package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.catalog.SongDraftForm;
import java.util.ArrayList;
import java.util.List;

/**
 * Multipart wrapper so Spring can bind {@code drafts[0].title},
 * {@code drafts[0].audio}, … from the P-06c audio tab.
 */
public class SongDraftBatchForm {

    private List<SongDraftForm> drafts = new ArrayList<>();

    public List<SongDraftForm> getDrafts() {
        return drafts;
    }

    public void setDrafts(List<SongDraftForm> drafts) {
        this.drafts = drafts == null ? new ArrayList<>() : drafts;
    }
}
