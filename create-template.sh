#!/bin/bash
# Usage: bash <(curl -s https://raw.githubusercontent.com/RegiByte/clojure-nexus/main/create-template.sh) my-new-app

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
TEMPLATE_REPO="https://github.com/RegiByte/clojure-nexus.git"
TEMPLATE_BRANCH="main"

# Helper functions
log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

# Validate project name
validate_project_name() {
    local name=$1
    if [[ ! $name =~ ^[a-z][a-z0-9-]*$ ]]; then
        log_error "Invalid project name: '$name'"
        log_error "Project name must:"
        log_error "  - Start with a lowercase letter"
        log_error "  - Contain only lowercase letters, numbers, and hyphens"
        log_error "Examples: myapp, my-app, myapp123"
        exit 1
    fi
}

# Convert kebab-case to snake_case for file/directory names
to_snake_case() {
    echo "$1" | tr '-' '_'
}

# Capitalize first letter
capitalize() {
    echo "$(tr '[:lower:]' '[:upper:]' <<< ${1:0:1})${1:1}"
}

# Main script
main() {
    echo ""
    log_info "🚀 Nexus Template Generator"
    echo ""

    # Check if project name provided
    if [ -z "$1" ]; then
        log_error "Usage: $0 <project-name>"
        log_error "Example: $0 my-awesome-app"
        exit 1
    fi

    PROJECT_NAME=$1
    validate_project_name "$PROJECT_NAME"

    # Convert names
    PROJECT_NAMESPACE="$PROJECT_NAME"  # Keep kebab-case for namespaces
    PROJECT_SNAKE=$(to_snake_case "$PROJECT_NAME")  # snake_case for files/dirs
    PROJECT_CAPITALIZED=$(capitalize "$PROJECT_SNAKE")

    log_info "Project name: $PROJECT_NAME"
    log_info "Namespace (kebab-case): $PROJECT_NAMESPACE"
    log_info "File/Dir name (snake_case): $PROJECT_SNAKE"
    log_info "Capitalized: $PROJECT_CAPITALIZED"
    echo ""

    # Check if directory exists
    if [ -d "$PROJECT_NAME" ]; then
        log_error "Directory '$PROJECT_NAME' already exists!"
        exit 1
    fi

    # Check dependencies
    log_info "Checking dependencies..."
    command -v git >/dev/null 2>&1 || { log_error "git is required but not installed."; exit 1; }
    log_success "All dependencies found"
    echo ""

    # Clone repository
    log_info "Cloning Nexus template..."
    git clone --depth 1 --branch "$TEMPLATE_BRANCH" "$TEMPLATE_REPO" "$PROJECT_NAME" 2>&1 | grep -v "Cloning into" || true
    cd "$PROJECT_NAME"
    log_success "Template cloned"
    echo ""

    # Remove git history
    log_info "Removing template git history..."
    rm -rf .git
    log_success "Git history removed"
    echo ""

    # Replace in files
    log_info "Replacing 'nexus' with '$PROJECT_NAMESPACE' in files..."
    
    # Find all text files (excluding binary files, node_modules, target, data, uploads)
    find . -type f \
        -not -path "*/node_modules/*" \
        -not -path "*/target/*" \
        -not -path "*/data/*" \
        -not -path "*/uploads/*" \
        -not -path "*/.git/*" \
        -not -path "*/.*" \
        -not -name "*.jar" \
        -not -name "*.png" \
        -not -name "*.jpg" \
        -not -name "*.ico" \
        -not -name "*.svg" \
        -not -name "*.woff*" \
        -not -name "*.ttf" \
        -not -name "*.eot" \
        -not -name "pnpm-lock.yaml" \
        -print0 | while IFS= read -r -d '' file; do
        
        # Use different sed syntax based on OS
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS - Replace with kebab-case for namespaces
            sed -i '' "s/nexus/$PROJECT_NAMESPACE/g" "$file" 2>/dev/null || true
            sed -i '' "s/Nexus/$PROJECT_CAPITALIZED/g" "$file" 2>/dev/null || true
        else
            # Linux
            sed -i "s/nexus/$PROJECT_NAMESPACE/g" "$file" 2>/dev/null || true
            sed -i "s/Nexus/$PROJECT_CAPITALIZED/g" "$file" 2>/dev/null || true
        fi
    done
    
    log_success "File contents updated"
    echo ""

    # Rename directories (use snake_case)
    log_info "Renaming directories..."
    
    if [ -d "src/clj/nexus" ]; then
        mv "src/clj/nexus" "src/clj/$PROJECT_SNAKE"
        log_success "Renamed src/clj/nexus → src/clj/$PROJECT_SNAKE"
    fi
    
    if [ -d "dev/nexus" ]; then
        mv "dev/nexus" "dev/$PROJECT_SNAKE"
        log_success "Renamed dev/nexus → dev/$PROJECT_SNAKE"
    fi
    
    if [ -d "test/clj/nexus" ]; then
        mv "test/clj/nexus" "test/clj/$PROJECT_SNAKE"
        log_success "Renamed test/clj/nexus → test/clj/$PROJECT_SNAKE"
    fi

    if [ -f "frontend/.env.example" ]; then
        cp "frontend/.env.example" "frontend/.env.local"
        log_success "Copied frontend/.env.example → frontend/.env.local"
    fi
    
    echo ""

    # Rename migration files
    log_info "Renaming migration files..."
    
    for migration in resources/migrations/*nexus*.sql; do
        if [ -f "$migration" ]; then
            new_name=$(echo "$migration" | sed "s/nexus/$PROJECT_NAMESPACE/g")
            mv "$migration" "$new_name"
            log_success "Renamed $(basename "$migration") → $(basename "$new_name")"
        fi
    done
    
    echo ""

    # Clean up unnecessary files
    log_info "Cleaning up template-specific files..."
    rm -rf data/db/* 2>/dev/null || true
    rm -rf uploads/* 2>/dev/null || true
    rm -rf target/* 2>/dev/null || true
    rm -rf node_modules 2>/dev/null || true
    rm -f create-template.sh 2>/dev/null || true
    log_success "Cleanup complete"
    echo ""

    # Initialize new git repository
    log_info "Initializing new git repository..."
    git init
    git add .
    git commit -m "Initial commit from Nexus template" --quiet
    log_success "Git repository initialized"
    echo ""

    # Create initial config file
    log_info "Creating initial configuration..."
    if [ ! -f "resources/envs/dev.edn" ]; then
        cat > "resources/envs/dev.edn" << EOF
{:db-url "postgres://postgres:postgres@localhost:5436/$PROJECT_SNAKE"
 :port 3456
 :jwt-secret "change-me-to-a-secure-secret-minimum-32-chars"}
EOF
        log_success "Created resources/envs/dev.edn"
    fi
    echo ""

    # Print next steps
    echo ""
    log_success "🎉 Project '$PROJECT_NAME' created successfully!"
    echo ""
    echo "Next steps:"
    echo ""
    echo "  1. ${BLUE}cd $PROJECT_NAME${NC}"
    echo ""
    echo "  2. Start PostgreSQL:"
    echo "     ${BLUE}docker compose up -d${NC}"
    echo ""
    echo "  3. Run migrations:"
    echo "     ${BLUE}lein run migrate${NC}"
    echo ""
    echo "  4. Start the REPL:"
    echo "     ${BLUE}lein repl${NC}"
    echo ""
    echo "  5. In the REPL:"
    echo "     ${BLUE}(require '$PROJECT_NAMESPACE.user)${NC}"
    echo "     ${BLUE}($PROJECT_NAMESPACE.user/start)${NC}"
    echo ""
    echo "  6. Visit: ${GREEN}http://localhost:3456/api/docs${NC}"
    echo ""
    echo "📚 Documentation: README.md"
    echo ""
}

# Run main function
main "$@"