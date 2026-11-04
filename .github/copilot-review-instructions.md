## Kotlin

- Parameterized SQL queries (`?` or named params), never string concatenation
- Use Kotest matchers (`shouldBe`) in tests
- Prefer sealed classes for state modeling

## Norwegian text (all `.md` and user-facing strings)

- Use Norwegian bokmål for user-facing text
- Avoid unnecessary anglicisms when good Norwegian alternatives exist

## Security

- No secrets, tokens, or credentials in code
- SQL queries must be parameterized
- GitHub Actions pinned to full SHA with version comment

## Over-editing

Flag changes where the diff is disproportionate to the stated goal:

- Renamed variables or parameters not related to the fix
- Restructured working code without justification
- Added refactoring outside the PR scope
