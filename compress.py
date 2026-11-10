import os
import zipfile
from pathlib import Path

# ==================================================
# CONFIGURATION
# ==================================================

PROJECT_DIR = Path(".").resolve()

OUTPUT_ZIP = PROJECT_DIR.parent / f"{PROJECT_DIR.name}.zip"

COMPRESSION_LEVEL = 6

# ==================================================
# EXCLUDED DIRECTORIES
# Applied at any directory depth
# ==================================================

EXCLUDE_DIRS = {
    # Version control
    ".git",
    ".svn",
    ".hg",

    # Python
    ".venv",
    "venv",
    "env",
    "__pycache__",
    ".pytest_cache",
    ".mypy_cache",
    ".ruff_cache",
    ".tox",
    ".nox",
    "htmlcov",
    ".hypothesis",

    # Kotlin / Java / Android / Gradle
    ".gradle",
    ".kotlin",
    ".idea",
    ".cxx",
    ".externalNativeBuild",
    "build",
    "out",
    "target",

    # Android generated files
    ".android",
    "captures",

    # Web / Node.js
    "node_modules",
    ".next",
    ".nuxt",
    ".output",
    ".angular",
    ".svelte-kit",
    ".turbo",
    ".vercel",
    ".parcel-cache",
    ".vite",
    "coverage",
    "dist",

    # IDEs
    ".vscode",
    ".fleet",

    # OS
    "__MACOSX",
    "$RECYCLE.BIN",
    "System Volume Information",
}

# ==================================================
# EXCLUDED FILE PATTERNS
# ==================================================

EXCLUDE_FILES = {
    # Python compiled files
    "*.pyc",
    "*.pyo",
    "*.pyd",

    # Kotlin / Java compiled files
    "*.class",
    "*.jar",
    "*.war",
    "*.ear",

    # Android generated / binary files
    "*.apk",
    "*.aab",
    "*.dex",
    "*.odex",
    "*.vdex",
    "*.so",

    # Native compiled binaries
    "*.o",
    "*.obj",
    "*.dll",
    "*.exe",
    "*.bin",

    # Logs
    "*.log",
    "*.log.*",

    # Temporary files
    "*.tmp",
    "*.temp",
    "*.swp",
    "*.swo",
    "*.bak",
    "*.orig",

    # OS generated
    ".DS_Store",
    "Thumbs.db",
    "desktop.ini",

    # Editor generated
    "*.iml",
    "*.sublime-workspace",
}

# ==================================================
# EXCLUDED EXACT FILES
# ==================================================

EXCLUDE_EXACT_FILES = {
    ".env",
    ".env.local",
    ".env.development.local",
    ".env.test.local",
    ".env.production.local",

    # Local machine configuration
    "local.properties",

    # Dependency lock / generated metadata (optional)
    # Keep lockfiles by default for reproducible builds.
}

# ==================================================
# EXCLUSION LOGIC
# ==================================================

def should_exclude(relative_path: Path) -> bool:
    parts = relative_path.parts
    filename = relative_path.name

    # Directory exclusions
    if any(part in EXCLUDE_DIRS for part in parts[:-1]):
        return True

    # Exact file exclusions
    if filename in EXCLUDE_EXACT_FILES:
        return True

    # Pattern exclusions
    if any(relative_path.match(pattern) for pattern in EXCLUDE_FILES):
        return True

    return False


# ==================================================
# ZIP CREATION
# ==================================================

def compress_project():

    print("=" * 55)
    print("         UNIVERSAL PROJECT COMPRESSOR")
    print("=" * 55)

    print(f"\nProject : {PROJECT_DIR}")
    print(f"Output  : {OUTPUT_ZIP}")
    print("\nCompressing...\n")

    file_count = 0
    skipped_count = 0
    original_size = 0

    with zipfile.ZipFile(
        OUTPUT_ZIP,
        mode="w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=COMPRESSION_LEVEL,
    ) as archive:

        for root, dirs, files in os.walk(PROJECT_DIR):

            root_path = Path(root)
            relative_root = root_path.relative_to(PROJECT_DIR)

            # Prune excluded directories
            filtered_dirs = []

            for directory in dirs:
                directory_path = relative_root / directory

                if directory in EXCLUDE_DIRS:
                    skipped_count += 1
                else:
                    filtered_dirs.append(directory)

            dirs[:] = filtered_dirs

            for filename in files:

                file_path = root_path / filename
                relative_path = file_path.relative_to(PROJECT_DIR)

                # Exclude unnecessary files
                if should_exclude(relative_path):
                    skipped_count += 1
                    continue

                # Prevent including the output ZIP
                if file_path.resolve() == OUTPUT_ZIP.resolve():
                    continue

                # Avoid including symbolic links
                if file_path.is_symlink():
                    skipped_count += 1
                    continue

                archive.write(
                    file_path,
                    arcname=relative_path.as_posix(),
                )

                file_count += 1
                original_size += file_path.stat().st_size

    compressed_size = OUTPUT_ZIP.stat().st_size

    savings = (
        (1 - compressed_size / original_size) * 100
        if original_size > 0 else 0
    )

    print("=" * 55)
    print("              COMPRESSION COMPLETE")
    print("=" * 55)

    print(f"\nFiles included : {file_count}")
    print(f"Items skipped : {skipped_count}")

    print(f"Original size : {original_size / (1024 * 1024):.2f} MB")
    print(f"ZIP size      : {compressed_size / (1024 * 1024):.2f} MB")
    print(f"Space saved   : {savings:.2f}%")

    print(f"\nSaved to      : {OUTPUT_ZIP}")
    print("=" * 55)


# ==================================================
# ENTRY POINT
# ==================================================

if __name__ == "__main__":
    compress_project()
