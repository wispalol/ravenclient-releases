package org.ravenclient.mod;

/**
 * A Modrinth version dependency. Mirrors OneLauncher's {@code VersionDependency}: at
 * least one of project/version is set, and only {@code required} dependencies are pulled
 * in automatically during installation.
 */
public record ModDependency(String project_id, String version_id, String dependency_type) {

    public boolean required() {
        return "required".equals(dependency_type);
    }
}
