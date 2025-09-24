#!/bin/bash

# Cross-platform Maven wrapper script
# Automatically detects and sets JAVA_HOME for different operating systems
# Requires Java 17 or higher

# Function to check Java version and return version number
check_java_version() {
    local java_home="$1"
    local java_executable="$java_home/bin/java"

    # On Windows, check for java.exe
    if [[ "$(uname -s)" == CYGWIN* || "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
        java_executable="$java_home/bin/java.exe"
    fi

    if [[ ! -x "$java_executable" ]]; then
        return 1
    fi

    # Get Java version
    local version_output=$("$java_executable" -version 2>&1 | head -1)
    local version_number

    # Extract version number (handles both old format like "1.8.0" and new format like "17.0.1")
    if [[ "$version_output" =~ \"1\.([0-9]+) ]]; then
        # Old format (Java 8 and below): "1.8.0_XXX"
        version_number=${BASH_REMATCH[1]}
    elif [[ "$version_output" =~ \"([0-9]+) ]]; then
        # New format (Java 9+): "17.0.1", "11.0.12", etc.
        version_number=${BASH_REMATCH[1]}
    else
        return 1
    fi

    echo "$version_number"
    return 0
}

# Function to find all Java installations and select the best one
find_and_select_java() {
    declare -A java_installations
    local paths_to_check=()

    echo "Scanning for Java installations..."

    # Build list of paths to check based on OS
    case "$(uname -s)" in
        Linux*)
            paths_to_check+=(
                "/usr/lib/jvm/java-17-openjdk"
                "/usr/lib/jvm/java-11-openjdk"
                "/usr/lib/jvm/java-8-openjdk"
                "/usr/lib/jvm/java-17-oracle"
                "/usr/lib/jvm/java-11-oracle"
                "/usr/lib/jvm/java-8-oracle"
                "/usr/lib/jvm/default-java"
                "/opt/java/openjdk-17"
                "/opt/java/openjdk-11"
                "/opt/java/openjdk-8"
                "/usr/lib/jvm/adoptopenjdk-17-hotspot"
                "/usr/lib/jvm/adoptopenjdk-11-hotspot"
                "/usr/lib/jvm/adoptopenjdk-8-hotspot"
            )
            # Add any jvm directory that exists
            if [[ -d "/usr/lib/jvm" ]]; then
                for jvm_dir in /usr/lib/jvm/*; do
                    if [[ -d "$jvm_dir" ]]; then
                        paths_to_check+=("$jvm_dir")
                    fi
                done
            fi
            ;;
        Darwin*)
            paths_to_check+=(
                "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
                "/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home"
                "/Library/Java/JavaVirtualMachines/adoptopenjdk-17.jdk/Contents/Home"
                "/Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home"
                "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
                "/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home"
                "$(/usr/libexec/java_home -v 17 2>/dev/null)"
                "$(/usr/libexec/java_home -v 11 2>/dev/null)"
                "$(/usr/libexec/java_home 2>/dev/null)"
            )
            # Add all JDK installations
            if [[ -d "/Library/Java/JavaVirtualMachines" ]]; then
                for jvm_dir in /Library/Java/JavaVirtualMachines/*.jdk; do
                    if [[ -d "$jvm_dir/Contents/Home" ]]; then
                        paths_to_check+=("$jvm_dir/Contents/Home")
                    fi
                done
            fi
            ;;
        CYGWIN*|MINGW*|MSYS*)
            paths_to_check+=(
                "/c/Program Files/Java/jdk-17"
                "/c/Program Files/Java/jdk-11"
                "/c/Program Files/Java/jdk-8"
            )
            # Add all Java installations in Program Files
            for java_dir in "/c/Program Files/Java"/* "/c/Program Files (x86)/Java"/*; do
                if [[ -d "$java_dir" ]]; then
                    paths_to_check+=("$java_dir")
                fi
            done
            ;;
    esac

    # Check current JAVA_HOME if set
    if [[ -n "$JAVA_HOME" ]]; then
        paths_to_check+=("$JAVA_HOME")
    fi

    # Try to derive from PATH
    if command -v java >/dev/null 2>&1; then
        local java_path=$(command -v java)
        local possible_java_home
        case "$(uname -s)" in
            CYGWIN*|MINGW*|MSYS*)
                possible_java_home=$(dirname "$(dirname "$java_path")")
                ;;
            *)
                java_path=$(readlink -f "$java_path" 2>/dev/null || echo "$java_path")
                possible_java_home=$(dirname "$(dirname "$java_path")")
                ;;
        esac
        if [[ -d "$possible_java_home" ]]; then
            paths_to_check+=("$possible_java_home")
        fi
    fi

    # Remove duplicates and check all paths
    declare -A seen_paths
    for path in "${paths_to_check[@]}"; do
        if [[ -n "$path" && -d "$path" && -z "${seen_paths[$path]}" ]]; then
            seen_paths["$path"]=1
            local version=$(check_java_version "$path")
            if [[ $? -eq 0 && -n "$version" ]]; then
                java_installations["$version"]="$path"
                echo "Found Java $version at: $path"
            fi
        fi
    done

    # Find the best Java version (17+ and closest to 17)
    local best_version=""
    local best_path=""

    # First, try to find Java 17+ versions
    for version in "${!java_installations[@]}"; do
        if [[ "$version" -ge 17 ]]; then
            if [[ -z "$best_version" || "$version" -lt "$best_version" ]]; then
                best_version="$version"
                best_path="${java_installations[$version]}"
            fi
        fi
    done

    if [[ -n "$best_version" ]]; then
        echo "Selected Java $best_version from: $best_path"
        export JAVA_HOME="$best_path"
        return 0
    else
        echo "Available Java versions found:"
        if [[ ${#java_installations[@]} -eq 0 ]]; then
            echo "  No Java installations detected"
        else
            for version in $(printf '%s\n' "${!java_installations[@]}" | sort -n); do
                echo "  Java $version at: ${java_installations[$version]}"
            done
        fi
        echo "Error: No Java 17+ installation found. This script requires Java 17 or higher."
        return 1
    fi
}

# Main execution
echo "Cross-platform Maven wrapper starting..."

# Find and set the best Java version
if ! find_and_select_java; then
    echo "Please ensure Java 17 or higher is installed."
    echo ""
    echo "Common installation commands:"
    echo "  Ubuntu/Debian: sudo apt install openjdk-17-jdk"
    echo "  CentOS/RHEL:   sudo yum install java-17-openjdk-devel"
    echo "  macOS:         brew install openjdk@17"
    echo "  Windows:       Download from https://adoptium.net/"
    exit 1
fi

# Add Java to PATH
export PATH="$JAVA_HOME/bin:$PATH"

# Verify Java is working
if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java not found in PATH after setting JAVA_HOME"
    echo "JAVA_HOME is set to: $JAVA_HOME"
    exit 1
fi

# Show Java version for confirmation
echo "Using Java version:"
java -version

echo "Running Maven with arguments: $*"
echo ""

# Execute Maven with all passed arguments
mvn "$@"
