package com.funix.swp490x.mrs.catalog;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Catalog staging, provider defaults and hosted-media settings (UC-28, UC-31). */
@ConfigurationProperties("mrs.catalog")
public class CatalogProperties {

    /** Bucket holding the staged song JSON. */
    private String bucket = "mrs-133857166188-assets";

    /** Key prefix within the bucket. Keep the trailing slash. */
    private String prefix = "song-data/";

    private String region = "ap-southeast-1";

    /** CLI profile for S3; blank uses the default credential chain, including the EC2 role. */
    private String awsProfile = "mrs-admin";

    /** Directory of staged JSON files to use instead of S3. */
    private String localDir = "";

    /** Provider defaults used when no database provider rows exist (SC-05). */
    private List<String> providers = List.of("EpidemicSound", "NCS", "OneOff");

    /** Import the whole prefix once at startup. Off by default. */
    private boolean importOnStart = false;

    private final Sync sync = new Sync();

    private final CoverArt coverArt = new CoverArt();

    private final Media media = new Media();

    /** Settings for sampling a bounded batch of cover colors during each import. */
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

        /** Delay from the end of one scheduled sync to the start of the next. */
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

    /** Upload prefixes and CDN URLs for hosted audio and artwork; JSON uses the catalog prefix. */
    public static class Media {

        /** Base URL stored in staged JSON for hosted media. */
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

        /** Maximum audio upload size: 100 MB. */
        private long maxAudioBytes = 100L * 1024 * 1024;

        /** Maximum artwork upload size: 5 MB. */
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

        /** Resolves hosted-media URLs to object keys; external provider URLs are excluded from deletion. */
        public Optional<String> hostedObjectKey(String publicUrl) {
            if (publicUrl == null || publicUrl.isBlank()) {
                return Optional.empty();
            }
            URI uri;
            try {
                uri = URI.create(publicUrl.trim());
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                return Optional.empty();
            }
            String key = path.startsWith("/") ? path.substring(1) : path;
            if (key.contains("..") || key.contains("\\")) {
                return Optional.empty();
            }
            if (!isHostedMediaKey(key)) {
                return Optional.empty();
            }
            return Optional.of(key);
        }

        /**
         * {@code song-data/{audio|artwork}/<slug>/<file>} — four segments, no
         * extra folders, so a crafted URL cannot aim at JSON or another prefix.
         */
        boolean isHostedMediaKey(String key) {
            if (key == null) {
                return false;
            }
            String audio = audioPrefix == null ? "song-data/audio/" : audioPrefix;
            String artwork = artworkPrefix == null ? "song-data/artwork/" : artworkPrefix;
            if (!key.startsWith(audio) && !key.startsWith(artwork)) {
                return false;
            }
            String[] parts = key.split("/");
            return parts.length == 4
                    && "song-data".equals(parts[0])
                    && ("audio".equals(parts[1]) || "artwork".equals(parts[1]))
                    && !parts[2].isEmpty()
                    && !parts[3].isEmpty();
        }
    }
}
