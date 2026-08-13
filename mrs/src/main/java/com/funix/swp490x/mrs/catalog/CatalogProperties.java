package com.funix.swp490x.mrs.catalog;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the song catalog is staged and which providers may enter it (UC-28).
 *
 * <p>One JSON object per song is written to
 * {@code s3://<bucket>/<prefix><externalSourceId>.json}; an import reads
 * that prefix and upserts into {@code song} / {@code tag} / {@code song_tag}.
 */
@ConfigurationProperties("mrs.catalog")
public class CatalogProperties {

    /** Bucket holding the staged song JSON. */
    private String bucket = "mrs-133857166188-assets";

    /** Key prefix within the bucket. Keep the trailing slash. */
    private String prefix = "song-data/";

    private String region = "ap-southeast-1";

    /**
     * Named profile for the S3 client. Defaults to {@code mrs-admin} so a
     * developer machine never silently reads another AWS account's bucket.
     * Clear it (empty string) on EC2 so the instance role is used instead.
     */
    private String awsProfile = "mrs-admin";

    /**
     * Set to a directory of {@code *.json} files to import from disk instead of
     * S3. Lets a fresh checkout and the test suite run the whole import path
     * with no AWS credentials.
     */
    private String localDir = "";

    /**
     * Providers allowed into the catalog (SC-05). An object naming anything
     * else is skipped and reported rather than silently accepted.
     */
    private List<String> providers = List.of("EpidemicSound", "NCS", "OneOff");

    /** Import the whole prefix once at startup. Off by default. */
    private boolean importOnStart = false;

    private final Sync sync = new Sync();

    private final CoverArt coverArt = new CoverArt();

    /**
     * Reading cover art to work out the shell's wash colours. Each import fills
     * in a bounded number of songs, so a scheduled sync stays short and a large
     * catalog finishes over several runs.
     */
    public static class CoverArt {

        private boolean enabled = true;

        /** Songs whose cover is read per import run. */
        private int batchSize = 2000;

        /** Per-cover budget. A slow CDN should not hold up the run. */
        private Duration timeout = Duration.ofSeconds(10);

        /** Covers larger than this are left alone rather than pulled into heap. */
        private int maxBytes = 8 * 1024 * 1024;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public int getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(int maxBytes) {
            this.maxBytes = maxBytes;
        }
    }

    /** The scheduled poller that picks up objects uploaded straight to S3. */
    public static class Sync {

        /** Off by default so dev machines and CI never poll. */
        private boolean enabled = false;

        /**
         * Delay between the end of one sync and the start of the next. An
         * unchanged prefix costs one listing and no object reads, so this can
         * be short without meaningful cost.
         */
        private Duration interval = Duration.ofMinutes(15);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAwsProfile() {
        return awsProfile;
    }

    public void setAwsProfile(String awsProfile) {
        this.awsProfile = awsProfile;
    }

    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }

    public List<String> getProviders() {
        return providers;
    }

    public void setProviders(List<String> providers) {
        this.providers = providers;
    }

    public boolean isImportOnStart() {
        return importOnStart;
    }

    public void setImportOnStart(boolean importOnStart) {
        this.importOnStart = importOnStart;
    }

    public Sync getSync() {
        return sync;
    }

    public CoverArt getCoverArt() {
        return coverArt;
    }
}
