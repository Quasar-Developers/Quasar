package dev.kate.erd.core.addon;
/**
 * Metadata about an ERD addon.
 * 
 * @param id unique identifier (e.g., "fusion_reactor")
 * @param name display name (e.g., "Fusion Reactor")
 * @param version semantic version (e.g., "1.0.0")
 * @param author addon author
 * @param description brief description
 */
public record AddonInfo(
    String id,
    String name,
    String version,
    String author,
    String description
) {
    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        private String id;
        private String name;
        private String version;
        private String author;
        private String description;
        public Builder id(String id) { 
            this.id = id; 
            return this; 
        }
        public Builder name(String name) { 
            this.name = name; 
            return this; 
        }
        public Builder version(String version) { 
            this.version = version; 
            return this; 
        }
        public Builder author(String author) { 
            this.author = author; 
            return this; 
        }
        public Builder description(String description) { 
            this.description = description; 
            return this; 
        }
        public AddonInfo build() {
            return new AddonInfo(id, name, version, author, description);
        }
    }
}
