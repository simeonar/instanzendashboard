# Security Guidelines

## Sensitive Information

This project requires proper configuration with sensitive information. **NEVER commit sensitive data to git.**

### ❌ DO NOT commit to git:

- Real IP addresses or IP ranges of your network
- Actual port numbers used in production
- Specific API endpoints or paths used in your infrastructure
- Authentication credentials
- Internal network structure details
- Database connection strings
- Any production configuration values

### ✅ Safe to commit:

- Example/placeholder IP addresses (e.g., `10.0.0.1`, `192.0.2.1`)
- Generic paths (e.g., `/api/health`, `/status`)
- Template files with placeholders
- Documentation with generic examples
- Default values that don't reveal infrastructure

## Configuration Files

### `application.properties` (EXCLUDED from git)
- Contains **real** configuration values
- Already in `.gitignore`
- Use this for actual deployment

### `application.properties.template` (INCLUDED in git)
- Contains **example** values only
- Use generic placeholders
- Safe for public repositories

## Before Committing

Always check for sensitive information:

```bash
# Check for IP addresses
git diff | grep -E "\b([0-9]{1,3}\.){3}[0-9]{1,3}\b"

# Check for suspicious patterns
git diff | grep -E "(password|secret|key|token|api_key)"
```

## Reporting Security Issues

If you find sensitive information in the repository:
1. **DO NOT** create a public issue
2. Contact the repository owner directly
3. Provide details about the leak location

## Default Values in Code

Default values in Java code (`ConfigManager.java`) use:
- Non-routable IP ranges (10.0.0.0/8)
- Standard ports (80, 443)
- Generic API paths

These are safe fallbacks and don't reveal actual infrastructure.
