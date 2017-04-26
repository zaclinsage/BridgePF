package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;

/** DAO for Shared Module Metadata. */
public interface SharedModuleMetadataDao {
    /** Creates the specified metadata object. */
    SharedModuleMetadata createMetadata(SharedModuleMetadata metadata);

    /** Deletes all metadata for module versions with the given ID. */
    void deleteMetadataByIdAllVersions(String id);

    /** Deletes metadata for the specified module ID and version. */
    void deleteMetadataByIdAndVersion(String id, int version);

    /** Gets metadata for the specified version of the specified module. */
    SharedModuleMetadata getMetadataByIdAndVersion(String id, int version);

    /**
     * <p>
     * Queries module metadata using the given SQL-like WHERE clause.
     * </p>
     * <p>
     * Example: "published = true AND os = 'iOS'"
     * </p>
     */
    List<SharedModuleMetadata> queryMetadata(String whereClause);

    /** Updates the specified metadata object. */
    SharedModuleMetadata updateMetadata(SharedModuleMetadata metadata);
}
