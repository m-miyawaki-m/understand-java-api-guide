# Security Rules

- **REQUIRED**: Never hardcode secrets (API keys, passwords). Use environment variables
- **REQUIRED**: Always validate user input
- **REQUIRED**: Use parameterized queries for SQL. Never build SQL with string concatenation
- **REQUIRED**: Include .env files in .gitignore
- XSS prevention: Escape user input when rendering HTML
- Keep dependencies minimal and check for known vulnerabilities
