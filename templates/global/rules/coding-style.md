# Coding Style

- Prefer immutability: use const, readonly, Readonly<T>
- Keep files under 300 lines. Split if larger
- Keep functions under 50 lines
- Max 3 levels of nesting. Use early returns to flatten
- Extract magic numbers into named constants
- No any type. Use unknown + type guards
- No console.log in production code
