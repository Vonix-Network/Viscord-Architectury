---
inclusion: always
---

# VonixSiteConnect Versioning and Changelog Guidelines

## Version Management

This project follows **Semantic Versioning 2.0.0** (https://semver.org/):

- **MAJOR** version (X.0.0): Incompatible API changes, major rewrites
- **MINOR** version (0.X.0): New features, backwards-compatible
- **PATCH** version (0.0.X): Bug fixes, backwards-compatible

### Current Version: 0.1.0

## Changelog Maintenance

The CHANGELOG.md MUST be updated for every change following Keep a Changelog format:

### Categories (in order):
1. **Added** - New features
2. **Changed** - Changes in existing functionality
3. **Deprecated** - Soon-to-be removed features
4. **Removed** - Removed features
5. **Fixed** - Bug fixes
6. **Security** - Security fixes

### Rules:
- Always update the `[Unreleased]` section first
- When releasing, move `[Unreleased]` content to a new version section
- Include the date in ISO format (YYYY-MM-DD)
- Keep entries concise but descriptive
- Group related changes together
- Update version links at the bottom

### Example Entry:
```markdown
## [Unreleased]

### Added
- New feature description

## [0.2.0] - 2026-03-26

### Added
- Account linking status command
- Configurable link expiration

### Fixed
- Rank sync timing issue on server restart
```

## Version Bumping Checklist

When bumping version:

1. Update CHANGELOG.md:
   - Move [Unreleased] to new version section
   - Add release date
   - Update version comparison links

2. Update gradle.properties in ALL version templates:
   - `mod_version=X.Y.Z`

3. Update version constant in main mod class:
   - `public static final String VERSION = "X.Y.Z";`

4. Git tag the release:
   - `git tag -a vX.Y.Z -m "Release X.Y.Z"`
   - `git push origin vX.Y.Z`

## Pre-Release Versions

For development builds:
- Alpha: `0.1.0-alpha.1`
- Beta: `0.1.0-beta.1`
- Release Candidate: `0.1.0-rc.1`

## AI Assistant Instructions

When making changes to VonixSiteConnect:

1. **ALWAYS** update CHANGELOG.md in the [Unreleased] section
2. Categorize changes appropriately (Added, Changed, Fixed, etc.)
3. If the change warrants a version bump, ask the user first
4. When bumping versions, follow the checklist above
5. Keep changelog entries user-focused, not implementation-focused
6. Reference issue numbers when applicable

## Version History

- **0.1.0** (2026-03-25): Initial release with account linking, rank sync, and stats sync
