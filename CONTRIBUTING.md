# Contributing to VoiceNotes

Thank you for considering contributing to VoiceNotes.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/Akbar02Work/VoiceNotes/issues)
2. If not, create a new issue with:
   - Clear description of the problem
   - Steps to reproduce
   - Expected vs actual behavior
   - Device info and Android version

### Suggesting Features

Open an issue with the `enhancement` label describing your idea.

### Pull Requests

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes following our guidelines below
4. Commit: `git commit -m 'Add amazing feature'`
5. Push: `git push origin feature/amazing-feature`
6. Open a Pull Request

## Development Guidelines

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions small and focused

### Architecture

- **MVVM pattern** — ViewModel handles UI logic
- **Repository pattern** — Repository abstracts data sources
- **Dependency Injection** — Use Hilt for DI
- **Compose** — All UI in Jetpack Compose

### Testing

- Write unit tests for ViewModels and Repositories
- Use MockK for mocking
- Run tests before submitting PR: `./gradlew testDebugUnitTest`

### Commit Messages

Follow conventional commits:
- `feat:` — new feature
- `fix:` — bug fix
- `docs:` — documentation
- `refactor:` — code refactoring
- `test:` — adding tests
- `chore:` — maintenance

## Getting Started

```bash
# Clone your fork
git clone https://github.com/Akbar02Work/VoiceNotes.git

# Create feature branch
git checkout -b feature/my-feature

# Install dependencies and build
./gradlew assembleDebug

# Run tests
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Questions?

Feel free to open an issue or start a discussion!
