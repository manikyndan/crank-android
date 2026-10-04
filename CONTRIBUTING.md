# Contributing to crank-android

Thanks for your interest in contributing! Please follow these guidelines.

## Getting Started
1. Fork the repository and clone your fork.
2. Create a feature branch: `git checkout -b feat/my-feature`.
3. Install JDK 17 and the Android SDK (API 37).
4. Build: `./gradlew assembleDebug`.

## Code Style
- Kotlin official code style (`kotlin.code.style=official`).
- Prefer `val` over `var`, explicit typing where helpful, null safety first.
- Follow Clean Architecture + MVVM: `domain/` has no Android dependencies.

## Commits
- Use conventional commits: `feat:`, `fix:`, `docs:`, `test:`, `chore:`.

## Testing
- Run `./gradlew testDebugUnitTest` before pushing.
- Add unit tests (JUnit + MockK) for use cases and ViewModels.

## Pull Requests
1. Fill in the PR template.
2. Link the related issue.
3. Ensure CI (lint + unit tests + assembleDebug) is green.
