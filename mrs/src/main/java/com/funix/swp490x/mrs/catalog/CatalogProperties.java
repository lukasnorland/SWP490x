package com.funix.swp490x.mrs.catalog;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final Media media = new Media();

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

    public Media getMedia() {
        return media;
    }

    /**
     * Company-hosted audio and cover art uploaded from P-06c. JSON staging
     * still lives on {@link #prefix}; this block is only the binaries and the
     * public URL the staged JSON points at.
     */
    public static class Media {

        /**
         * Origin the player and the shell load media from. CloudFront in
         * production; a local store still writes the same URL shape so the
         * JSON is identical to what an S3 upload produces.
         */
        private String publicBaseUrl = "https://d34ixswlpjs53y.cloudfront.net";

        private String audioPrefix = "song-data/audio/";

        private String artworkPrefix = "song-data/artwork/";

        /**
         * Folder name under the audio/artwork prefixes for each registered
         * provider. Keys match {@code mrs.catalog.providers}.
         */
        private Map<String, String> vendorSlugs = new LinkedHashMap<>(Map.of(
                "EpidemicSound", "epidemic",
                "NCS", "ncs",
                "OneOff", "one-off"));

        /** 100 MB — a full-length 16-bit WAV; 24-bit masters should be MP3. */
        private long maxAudioBytes = 100L * 1024 * 1024;

        /** 5 MB — covers are JPEGs, not print masters. */
        private long maxCoverBytes = 5L * 1024 * 1024;

        private List<String> audioTypes = List.of(
                "audio/mpeg", "audio/wav", "audio/flac", "audio/mp4", "audio/ogg");

        private List<String> coverTypes = List.of(
                "image/jpeg", "image/png", "image/webp");

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public String getAudioPrefix() {
            return audioPrefix;
        }

        public void setAudioPrefix(String audioPrefix) {
            this.audioPrefix = audioPrefix;
        }

        public String getArtworkPrefix() {
            return artworkPrefix;
        }

        public void setArtworkPrefix(String artworkPrefix) {
            this.artworkPrefix = artworkPrefix;
        }

        public Map<String, String> getVendorSlugs() {
            return vendorSlugs;
        }

        public void setVendorSlugs(Map<String, String> vendorSlugs) {
            this.vendorSlugs = vendorSlugs;
        }

        public long getMaxAudioBytes() {
            return maxAudioBytes;
        }

        public void setMaxAudioBytes(long maxAudioBytes) {
            this.maxAudioBytes = maxAudioBytes;
        }

        public long getMaxCoverBytes() {
            return maxCoverBytes;
        }

        public void setMaxCoverBytes(long maxCoverBytes) {
            this.maxCoverBytes = maxCoverBytes;
        }

        public List<String> getAudioTypes() {
            return audioTypes;
        }

        public void setAudioTypes(List<String> audioTypes) {
            this.audioTypes = audioTypes;
        }

        public List<String> getCoverTypes() {
            return coverTypes;
        }

        public void setCoverTypes(List<String> coverTypes) {
            this.coverTypes = coverTypes;
        }

        /**
         * Folder slug for a registered provider, or {@code null} when the
         * provider has no mapping (treated as a validation error).
         */
        public String slugFor(String sourceProvider) {
            if (sourceProvider == null || vendorSlugs == null) {
                return null;
            }
            String mapped = vendorSlugs.get(sourceProvider);
            if (mapped != null) {
                return mapped;
            }
            return vendorSlugs.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(sourceProvider))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        /** HTTPS URL the staged JSON stores for a key under this bucket. */
        public String publicUrl(String key) {
            String base = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
            String path = key.startsWith("/") ? key : "/" + key;
            return base + path;
        }
    }
}
